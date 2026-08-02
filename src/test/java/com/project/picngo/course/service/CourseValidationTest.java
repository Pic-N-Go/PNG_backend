package com.project.picngo.course.service;

import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.CourseErrorCode;
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
        courseService.syncCourseSpots(userId, courseId, syncReq);

        verify(courseRepository, times(1)).save(any(Course.class));
    }
}
