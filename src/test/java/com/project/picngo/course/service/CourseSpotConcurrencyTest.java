package com.project.picngo.course.service;

import com.project.picngo.common.domain.SpotCategory;
import com.project.picngo.course.domain.Course;
import com.project.picngo.course.dto.CourseSpotSyncItem;
import com.project.picngo.course.dto.CourseSpotSyncRequest;
import com.project.picngo.course.repository.CourseRepository;
import com.project.picngo.course.repository.CourseSpotRepository;
import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.domain.enums.SpotSource;
import com.project.picngo.spot.domain.enums.SpotStatus;
import com.project.picngo.spot.repository.SpotRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 코스 스팟 동기화의 동시성 문제를 재현한다.
 *
 * <p><b>이 테스트들은 "고쳐진 동작"이 아니라 "지금 일어나는 일"을 고정한 것이다.</b>
 * 동시성 제어를 넣기 전에 문제가 실재하는지부터 증명하려고 먼저 작성했다.
 * 낙관적 락을 적용하면 아래 단언들은 "충돌이 감지된다"로 바뀌어야 한다.
 *
 * <p>동기화 API는 "보낸 목록이 곧 전부"인 전체 덮어쓰기 방식이다. 이는 순서 변경 때마다
 * 네트워크 호출과 경로 재계산이 일어나는 것을 막기 위한 의도된 설계다. 다만 그 전제는
 * <b>"클라이언트가 항상 최신 목록을 보낸다"</b>인데, 아래 두 경우에 그 전제가 깨진다.
 *
 * <p>트랜잭션을 테스트에 걸지 않는다(@Transactional 없음). 걸면 모든 호출이 한 트랜잭션에
 * 묶여서, 커밋 이후에 벌어지는 이 문제들이 재현되지 않는다.
 */
@SpringBootTest
@ActiveProfiles("test")
class CourseSpotConcurrencyTest {

    private static final Long USER_ID = 42L;

    @Autowired
    private CourseService courseService;
    @Autowired
    private CourseRepository courseRepository;
    @Autowired
    private CourseSpotRepository courseSpotRepository;
    @Autowired
    private SpotRepository spotRepository;

    private Long courseId;
    private List<Long> spotIds;

    @BeforeEach
    void setUp() {
        courseSpotRepository.deleteAll();
        courseRepository.deleteAll();

        spotIds = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            Spot spot = spotRepository.save(Spot.builder()
                    .name("테스트스팟" + i)
                    .address("서울특별시 종로구 " + i)
                    .latitude(37.5 + i * 0.01)
                    .longitude(127.0 + i * 0.01)
                    .categories(Set.of(SpotCategory.PARK))
                    .status(SpotStatus.APPROVED)
                    .source(SpotSource.TOUR_API)
                    .build());
            spotIds.add(spot.getId());
        }

        Course course = courseRepository.save(Course.builder()
                .userId(USER_ID)
                .title("동시성 테스트 코스")
                .startDate(LocalDate.of(2026, 9, 1))
                .endDate(LocalDate.of(2026, 9, 3))
                .build());
        courseId = course.getId();

        // 스팟 3개를 담아둔 상태에서 시작한다.
        List<CourseSpotSyncItem> initial = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            initial.add(new CourseSpotSyncItem(null, spotIds.get(i), 1, i + 1, null));
        }
        courseService.syncCourseSpots(USER_ID, courseId, new CourseSpotSyncRequest(initial));
    }

    @AfterEach
    void tearDown() {
        courseSpotRepository.deleteAll();
        courseRepository.deleteAll();
        spotRepository.deleteAll();
    }

    /** 지금 DB에 저장된 코스 스팟을 그대로 동기화 요청 항목으로 바꾼다(= 클라이언트가 화면에서 들고 있는 목록). */
    private List<CourseSpotSyncItem> currentSnapshot() {
        return courseSpotRepository.findAll().stream()
                .filter(cs -> cs.getCourse().getId().equals(courseId))
                .map(cs -> new CourseSpotSyncItem(
                        cs.getId(), cs.getSpotId(), cs.getDayNumber(), cs.getSequenceOrder(), cs.getMemo()))
                .toList();
    }

    private long courseSpotCount() {
        return courseSpotRepository.findAll().stream()
                .filter(cs -> cs.getCourse().getId().equals(courseId))
                .count();
    }

    @Test
    @DisplayName("[현재 동작] 다른 기기가 추가한 스팟이, 낡은 목록을 저장하는 순간 사라진다")
    void staleListSilentlyDeletesAnotherDevicesSpot() {
        // 1) 기기 A(태블릿)가 코스 화면을 연다. 이때의 목록을 그대로 들고 있는다.
        List<CourseSpotSyncItem> deviceASnapshot = currentSnapshot();
        assertThat(deviceASnapshot).hasSize(3);

        // 2) 그 사이 기기 B(휴대폰)가 스팟을 하나 추가한다.
        List<CourseSpotSyncItem> deviceBRequest = new ArrayList<>(deviceASnapshot);
        deviceBRequest.add(new CourseSpotSyncItem(null, spotIds.get(3), 1, 4, null));
        courseService.syncCourseSpots(USER_ID, courseId, new CourseSpotSyncRequest(deviceBRequest));

        assertThat(courseSpotCount()).as("기기 B의 추가가 반영됐다").isEqualTo(4);

        // 3) 기기 A가 순서만 바꿔 저장한다. 목록은 1)에서 들고 있던 낡은 것이다.
        courseService.syncCourseSpots(USER_ID, courseId, new CourseSpotSyncRequest(deviceASnapshot));

        // 4) A의 목록에 없던 스팟이 "요청에 없으니 지우라는 뜻"으로 해석되어 삭제된다.
        //    A는 순서만 바꿨을 뿐인데 B의 작업이 사라졌고, 아무도 에러를 보지 못했다.
        //
        //    ⚠️ 이 경우는 낙관적 락을 붙여도 아직 막히지 않는다. 서버가 저장 직전에
        //    코스를 새로 읽어 항상 최신 버전을 보기 때문이다. A가 "내가 본 건 버전 5"라고
        //    알려주지 않는 한, 서버는 A의 목록이 낡았다는 사실을 알 방법이 없다.
        //    → 다음 단계: 요청에 클라이언트가 본 버전을 실어 보내고 서버가 대조한다.
        assertThat(courseSpotCount())
                .as("갱신 유실: 기기 B가 추가한 스팟이 조용히 삭제된다 (클라이언트 버전 전달 전까지 남는 문제)")
                .isEqualTo(3);
    }

    @Test
    @DisplayName("[현재 동작] 순서만 바꾸는 저장은 동시에 두 번 해도 안전하다")
    void reorderOnlyIsSafeEvenWhenSentTwice() {
        // 순서 변경 요청에는 courseSpotId가 전부 들어있다. 삭제 대상이 없고,
        // 같은 요청을 두 번 처리해도 결과가 같다. 이 경우는 고칠 필요가 없음을 고정해둔다.
        List<CourseSpotSyncItem> reordered = new ArrayList<>();
        List<CourseSpotSyncItem> snapshot = currentSnapshot();
        for (int i = 0; i < snapshot.size(); i++) {
            CourseSpotSyncItem item = snapshot.get(i);
            reordered.add(new CourseSpotSyncItem(
                    item.courseSpotId(), item.spotId(), 1, snapshot.size() - i, item.memo()));
        }

        courseService.syncCourseSpots(USER_ID, courseId, new CourseSpotSyncRequest(reordered));
        courseService.syncCourseSpots(USER_ID, courseId, new CourseSpotSyncRequest(reordered));

        assertThat(courseSpotCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("신규 스팟 추가를 동시에 두 번 보내면 한쪽만 성공한다 - 중복 생성 차단")
    void doubleSubmitIsRejectedByOptimisticLock() throws Exception {
        // 신규 스팟은 courseSpotId가 null이라 서버가 매번 "새로 만들어라"로 해석한다.
        // 낙관적 락이 없을 때는 두 요청이 둘 다 새로 만들어 스팟이 5개가 됐다
        // (실측: 코스 스팟 수=5, 실패한 요청=0).
        List<CourseSpotSyncItem> request = new ArrayList<>(currentSnapshot());
        request.add(new CourseSpotSyncItem(null, spotIds.get(3), 1, 4, null));

        CountDownLatch startLine = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(2);
        AtomicInteger failures = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            for (int i = 0; i < 2; i++) {
                pool.submit(() -> {
                    try {
                        startLine.await();
                        courseService.syncCourseSpots(USER_ID, courseId, new CourseSpotSyncRequest(request));
                    } catch (Exception e) {
                        failures.incrementAndGet();
                    } finally {
                        finished.countDown();
                    }
                });
            }
            startLine.countDown();
            assertThat(finished.await(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        long actual = courseSpotCount();
        System.out.printf("[동시 저장 결과] 코스 스팟 수=%d (정상=4, 중복=5), 실패한 요청=%d%n",
                actual, failures.get());

        assertThat(actual)
                .as("기존 3개 + 신규 1개. 5개면 두 요청이 각각 새로 만든 것이다")
                .isEqualTo(4);
        assertThat(failures.get())
                .as("나중에 커밋한 쪽은 버전이 어긋나 거부되어야 한다")
                .isEqualTo(1);
    }
}
