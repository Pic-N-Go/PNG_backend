package com.project.picngo.course.service;

import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.CourseErrorCode;
import com.project.picngo.common.exception.code.SpotErrorCode;
import com.project.picngo.course.domain.Course;
import com.project.picngo.course.dto.CourseCreateRequest;
import com.project.picngo.course.dto.CourseSpotSyncItem;
import com.project.picngo.course.dto.CourseSpotSyncRequest;
import com.project.picngo.course.repository.CourseChecklistRepository;
import com.project.picngo.course.repository.CourseRepository;
import com.project.picngo.course.repository.CourseSpotRepository;
import com.project.picngo.spot.repository.SpotRepository;
import com.project.picngo.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CourseValidationTest {

    @Mock
    private CourseRepository courseRepository;
    @Mock
    private CourseSpotRepository courseSpotRepository;
    @Mock
    private CourseChecklistRepository courseChecklistRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private SpotRepository spotRepository;

    @InjectMocks
    private CourseService courseService;

    @Test
    @DisplayName("코스 시작일이 종료일보다 뒤면 INVALID_COURSE_DATE_RANGE 예외 발생")
    void testCreateCourse_InvalidDateRange() {
        Long userId = 1L;
        when(userRepository.existsById(userId)).thenReturn(true);

        CourseCreateRequest request = new CourseCreateRequest(
                "제주 여행",
                LocalDate.of(2026, 8, 10),
                LocalDate.of(2026, 8, 5) // 시작일 > 종료일
        );

        assertThatThrownBy(() -> courseService.createCourse(userId, request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(CourseErrorCode.INVALID_COURSE_DATE_RANGE);
    }

    @Test
    @DisplayName("코스 기간이 15일을 초과(16일)하면 EXCEEDED_MAX_COURSE_DAYS 예외 발생")
    void testCreateCourse_ExceedMaxDays() {
        Long userId = 1L;
        when(userRepository.existsById(userId)).thenReturn(true);

        CourseCreateRequest request = new CourseCreateRequest(
                "장기 출사 여행",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 16) // 16일간 (8/1 ~ 8/16)
        );

        assertThatThrownBy(() -> courseService.createCourse(userId, request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(CourseErrorCode.EXCEEDED_MAX_COURSE_DAYS);
    }

    @Test
    @DisplayName("하루(DAY 1)에 11개 스팟 동기화 시 EXCEEDED_MAX_DAY_SPOTS 예외 발생")
    void testSyncCourseSpots_ExceedMaxDaySpots() {
        Long userId = 1L;
        Long courseId = 10L;

        Course course = Course.builder()
                .userId(userId)
                .title("테스트 코스")
                .startDate(LocalDate.of(2026, 8, 1))
                .endDate(LocalDate.of(2026, 8, 3))
                .build();

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));

        // Day 1에 11개 스팟 구성
        List<CourseSpotSyncItem> items = new ArrayList<>();
        for (int i = 1; i <= 11; i++) {
            items.add(new CourseSpotSyncItem(null, (long) i, 1, i, "메모 " + i));
        }

        CourseSpotSyncRequest request = new CourseSpotSyncRequest(items);

        assertThatThrownBy(() -> courseService.syncCourseSpots(userId, courseId, request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(CourseErrorCode.EXCEEDED_MAX_DAY_SPOTS);
    }

    @Test
    @DisplayName("정상 범위(15일 이내, Day당 10개 이내) 코스 생성 및 스팟 동기화 성공")
    void testCourseValidation_Success() {
        Long userId = 1L;
        Long courseId = 10L;

        when(userRepository.existsById(userId)).thenReturn(true);

        // 15일간 코스
        CourseCreateRequest createReq = new CourseCreateRequest(
                "정상 15일 코스",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 15)
        );

        Course course = Course.builder()
                .userId(userId)
                .title(createReq.title())
                .startDate(createReq.startDate())
                .endDate(createReq.endDate())
                .build();

        when(courseRepository.save(any(Course.class))).thenReturn(course);
        courseService.createCourse(userId, createReq);

        // Day 1에 10개 스팟
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        List<CourseSpotSyncItem> items = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            items.add(new CourseSpotSyncItem(null, (long) i, 1, i, "메모 " + i));
        }

        CourseSpotSyncRequest syncReq = new CourseSpotSyncRequest(items);
        // 신규 추가되는 spotId 10개가 모두 실존하는 상황
        when(spotRepository.countByIdIn(anyCollection())).thenReturn(10L);

        courseService.syncCourseSpots(userId, courseId, syncReq);

        verify(courseRepository, times(1)).save(any(Course.class));
    }

    @Test
    @DisplayName("존재하지 않는 spotId로 스팟을 추가하면 저장 전에 거부한다")
    void testSyncCourseSpots_RejectsUnknownSpotId() {
        Long userId = 1L;
        Long courseId = 10L;

        Course course = Course.builder()
                .userId(userId)
                .title("제주 코스")
                .startDate(LocalDate.of(2026, 8, 1))
                .endDate(LocalDate.of(2026, 8, 3))
                .build();

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));

        // 요청한 2건 중 1건만 실존한다
        CourseSpotSyncRequest syncReq = new CourseSpotSyncRequest(List.of(
                new CourseSpotSyncItem(null, 1L, 1, 1, "실존하는 스팟"),
                new CourseSpotSyncItem(null, 999999L, 1, 2, "존재하지 않는 스팟")
        ));
        when(spotRepository.countByIdIn(anyCollection())).thenReturn(1L);

        assertThatThrownBy(() -> courseService.syncCourseSpots(userId, courseId, syncReq))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(SpotErrorCode.SPOT_NOT_FOUND);

        // 한 건이라도 유효하지 않으면 아무것도 저장하지 않는다
        verify(courseSpotRepository, never()).save(any());
    }

    @Test
    @DisplayName("코스 목록 조회 시 진행중/진행예정 코스 상단 정렬 및 시작날짜 오름차순 정렬 검증")
    void testGetCourses_SortingByStatusAndStartDate() {
        Long userId = 1L;
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));

        // 1. 이미 완료된 코스 (종료일이 과거)
        Course completedCourse = Course.builder()
                .userId(userId)
                .title("완료된 코스")
                .startDate(today.minusDays(10))
                .endDate(today.minusDays(5))
                .build();

        // 2. 진행 예정 코스 A (시작일이 오늘 이후 - 빠른 날짜)
        Course upcomingCourse1 = Course.builder()
                .userId(userId)
                .title("진행 예정 코스 1")
                .startDate(today.plusDays(2))
                .endDate(today.plusDays(4))
                .build();

        // 3. 진행 예정 코스 B (시작일이 오늘 이후 - 느린 날짜)
        Course upcomingCourse2 = Course.builder()
                .userId(userId)
                .title("진행 예정 코스 2")
                .startDate(today.plusDays(10))
                .endDate(today.plusDays(12))
                .build();

        // DB에는 완료된 코스, 늦은 예정 코스, 빠른 예정 코스 순서로 저장되어 있음
        when(courseRepository.findAllByUserId(userId)).thenReturn(List.of(completedCourse, upcomingCourse2, upcomingCourse1));

        List<com.project.picngo.course.dto.CourseResponse> result = courseService.getCourses(userId);

        // 결과 정렬 순서 검증: [진행 예정 코스 1 (빠른 시작일)] -> [진행 예정 코스 2 (느린 시작일)] -> [완료된 코스 (하단)]
        org.assertj.core.api.Assertions.assertThat(result).hasSize(3);
        org.assertj.core.api.Assertions.assertThat(result.get(0).title()).isEqualTo("진행 예정 코스 1");
        org.assertj.core.api.Assertions.assertThat(result.get(1).title()).isEqualTo("진행 예정 코스 2");
        org.assertj.core.api.Assertions.assertThat(result.get(2).title()).isEqualTo("완료된 코스");
    }
}
