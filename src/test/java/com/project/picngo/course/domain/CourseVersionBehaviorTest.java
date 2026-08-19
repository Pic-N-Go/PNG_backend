package com.project.picngo.course.domain;

import com.project.picngo.common.domain.SpotCategory;
import com.project.picngo.course.dto.CourseSpotSyncItem;
import com.project.picngo.course.dto.CourseSpotSyncRequest;
import com.project.picngo.course.repository.CourseRepository;
import com.project.picngo.course.repository.CourseSpotRepository;
import com.project.picngo.course.service.CourseService;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code @Version}이 <b>언제 오르는지</b>를 실측한다.
 *
 * <p>낙관적 락을 붙였다고 다 막히는 게 아니다. JPA는 "그 엔티티 자신이 변했을 때"
 * 버전을 올리는데, 코스 스팟 순서 변경은 자식(CourseSpot)의 필드만 바꾼다.
 * 부모(Course)의 버전이 안 오르면 <b>락을 붙였는데 아무것도 막지 못하는</b> 상태가 된다.
 * 가장 위험한 경우다 - 적용했다고 믿고 넘어가게 되니까.
 *
 * <p>그래서 추측하지 않고 세 경우를 각각 재본다:
 * 스팟 추가(컬렉션에 add) / 스팟 삭제(컬렉션에서 remove) / 순서만 변경(자식 필드만 수정).
 */
@SpringBootTest
@ActiveProfiles("test")
class CourseVersionBehaviorTest {

    private static final Long USER_ID = 77L;

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
        for (int i = 1; i <= 3; i++) {
            spotIds.add(spotRepository.save(Spot.builder()
                    .name("버전테스트스팟" + i)
                    .address("서울특별시 중구 " + i)
                    .latitude(37.55 + i * 0.01)
                    .longitude(126.97 + i * 0.01)
                    .categories(Set.of(SpotCategory.PARK))
                    .status(SpotStatus.APPROVED)
                    .source(SpotSource.TOUR_API)
                    .build()).getId());
        }

        courseId = courseRepository.save(Course.builder()
                .userId(USER_ID)
                .title("버전 거동 확인 코스")
                .startDate(LocalDate.of(2026, 10, 1))
                .endDate(LocalDate.of(2026, 10, 2))
                .build()).getId();

        // 스팟 2개를 담아둔 상태에서 시작한다.
        sync(item(null, 0, 1), item(null, 1, 2));
    }

    @AfterEach
    void tearDown() {
        courseSpotRepository.deleteAll();
        courseRepository.deleteAll();
        spotRepository.deleteAll();
    }

    private CourseSpotSyncItem item(Long courseSpotId, int spotIndex, int order) {
        return new CourseSpotSyncItem(courseSpotId, spotIds.get(spotIndex), 1, order, null);
    }

    private void sync(CourseSpotSyncItem... items) {
        courseService.syncCourseSpots(USER_ID, courseId, new CourseSpotSyncRequest(List.of(items)));
    }

    private long currentVersion() {
        return courseRepository.findById(courseId).orElseThrow().getVersion();
    }

    private List<CourseSpotSyncItem> snapshot() {
        return courseSpotRepository.findAll().stream()
                .filter(cs -> cs.getCourse().getId().equals(courseId))
                .sorted((a, b) -> a.getSequenceOrder().compareTo(b.getSequenceOrder()))
                .map(cs -> new CourseSpotSyncItem(
                        cs.getId(), cs.getSpotId(), cs.getDayNumber(), cs.getSequenceOrder(), cs.getMemo()))
                .toList();
    }

    @Test
    @DisplayName("스팟을 추가하면 코스 버전이 오른다")
    void versionIncrementsWhenSpotAdded() {
        long before = currentVersion();

        List<CourseSpotSyncItem> request = new ArrayList<>(snapshot());
        request.add(item(null, 2, 3));
        courseService.syncCourseSpots(USER_ID, courseId, new CourseSpotSyncRequest(request));

        assertThat(currentVersion())
                .as("컬렉션에 새 자식이 들어오면 부모 버전이 올라야 한다")
                .isGreaterThan(before);
    }

    @Test
    @DisplayName("스팟을 삭제하면 코스 버전이 오른다")
    void versionIncrementsWhenSpotRemoved() {
        long before = currentVersion();

        List<CourseSpotSyncItem> request = new ArrayList<>(snapshot());
        request.remove(request.size() - 1);
        courseService.syncCourseSpots(USER_ID, courseId, new CourseSpotSyncRequest(request));

        assertThat(currentVersion())
                .as("컬렉션에서 자식이 빠지면 부모 버전이 올라야 한다")
                .isGreaterThan(before);
    }

    @Test
    @DisplayName("순서만 바꾸면 코스 버전이 오르는가 - 여기가 관건이다")
    void versionBehaviorWhenOnlyChildFieldsChange() {
        long before = currentVersion();

        // 자식(CourseSpot)의 sequenceOrder만 뒤집는다. 컬렉션의 구성원은 그대로다.
        List<CourseSpotSyncItem> snapshot = snapshot();
        List<CourseSpotSyncItem> reordered = new ArrayList<>();
        for (int i = 0; i < snapshot.size(); i++) {
            CourseSpotSyncItem s = snapshot.get(i);
            reordered.add(new CourseSpotSyncItem(
                    s.courseSpotId(), s.spotId(), s.dayNumber(), snapshot.size() - i, s.memo()));
        }
        courseService.syncCourseSpots(USER_ID, courseId, new CourseSpotSyncRequest(reordered));

        long after = currentVersion();
        System.out.printf("[버전 거동] 순서만 변경: before=%d, after=%d → %s%n",
                before, after, after > before ? "올랐음" : "안 올랐음");

        // 처음 측정했을 때는 0 → 0으로 오르지 않았다. 자식 필드만 바뀌었으니
        // Hibernate가 Course를 "변했다"고 보지 않은 것이다.
        // syncCourseSpots에서 OPTIMISTIC_FORCE_INCREMENT로 강제 증가시켜 해결했다.
        assertThat(after)
                .as("순서 변경도 충돌 감지 대상이어야 한다 - 안 오르면 락이 무력화된다")
                .isGreaterThan(before);
    }
}
