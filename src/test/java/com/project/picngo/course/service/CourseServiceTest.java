package com.project.picngo.course.service;

import com.project.picngo.course.domain.Course;
import com.project.picngo.course.domain.CourseSpot;
import com.project.picngo.course.dto.CourseSpotAddRequest;
import com.project.picngo.course.dto.CourseSpotOrderUpdateRequest;
import com.project.picngo.course.dto.CourseSpotResponse;
import com.project.picngo.course.repository.CourseRepository;
import com.project.picngo.course.repository.CourseSpotRepository;
import com.project.picngo.external.DirectionsClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CourseServiceTest {

    @InjectMocks
    private CourseService courseService;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private CourseSpotRepository courseSpotRepository;

    @Mock
    private DirectionsClient directionsClient;

    private Course createCourseFixture() {
        Course course = Course.builder()
                .userId(1L)
                .title("테스트 코스")
                .build();
        ReflectionTestUtils.setField(course, "id", 1L);
        return course;
    }

    private CourseSpot createSpotFixture(Course course, Long id, int dayNumber, int sequenceOrder) {
        CourseSpot spot = CourseSpot.builder()
                .course(course)
                .spotId(id * 100)
                .dayNumber(dayNumber)
                .sequenceOrder(sequenceOrder)
                .memo("스팟 " + sequenceOrder)
                .build();
        ReflectionTestUtils.setField(spot, "id", id);
        return spot;
    }

    @Test
    @DisplayName("코스 스팟 추가 시 동선 재계산 검증")
    void addCourseSpot_recalculatesTravelTime() {
        // given
        Course course = createCourseFixture();
        CourseSpot spot1 = createSpotFixture(course, 1L, 1, 1);
        course.getCourseSpots().add(spot1);

        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(courseSpotRepository.save(any(CourseSpot.class))).thenAnswer(inv -> {
            CourseSpot saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 2L);
            return saved;
        });
        when(directionsClient.getTravelTimeMinutes(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(45); // 이동시간 45분으로 Mocking

        CourseSpotAddRequest request = new CourseSpotAddRequest(200L, 1, 2, "두번째 스팟");

        // when
        CourseSpotResponse response = courseService.addCourseSpot(1L, request);

        // then
        assertThat(course.getCourseSpots()).hasSize(2);
        
        // 첫 번째 스팟은 이동시간이 없어야 함 (null)
        assertThat(course.getCourseSpots().get(0).getTravelTimeMinutes()).isNull();
        // 두 번째 추가된 스팟은 이동시간이 45분으로 계산되어야 함
        assertThat(course.getCourseSpots().get(1).getTravelTimeMinutes()).isEqualTo(45);
        
        // 길찾기 API가 1번 호출되었는지 검증 (첫번째 스팟은 null 처리되므로 두번째 스팟에서 1번만 호출됨)
        verify(directionsClient, times(1)).getTravelTimeMinutes(anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    @DisplayName("코스 스팟 삭제 시 남은 동선 재계산 검증")
    void removeCourseSpot_recalculatesTravelTime() {
        // given
        Course course = createCourseFixture();
        CourseSpot spot1 = createSpotFixture(course, 1L, 1, 1);
        CourseSpot spot2 = createSpotFixture(course, 2L, 1, 2);
        CourseSpot spot3 = createSpotFixture(course, 3L, 1, 3);
        course.getCourseSpots().addAll(new ArrayList<>(List.of(spot1, spot2, spot3)));

        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(directionsClient.getTravelTimeMinutes(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(60); // 재계산 시 이동시간 60분으로 Mocking

        // when: 중간 스팟(spot2)을 삭제
        courseService.removeCourseSpot(1L, 2L);

        // then
        assertThat(course.getCourseSpots()).hasSize(2);
        verify(courseSpotRepository).delete(spot2);

        // 삭제 후 첫번째 자리는 spot1 (이동시간 null)
        assertThat(course.getCourseSpots().get(0).getId()).isEqualTo(1L);
        assertThat(course.getCourseSpots().get(0).getTravelTimeMinutes()).isNull();

        // 재계산 후 두번째 자리는 spot3 (이동시간 60분)
        assertThat(course.getCourseSpots().get(1).getId()).isEqualTo(3L);
        assertThat(course.getCourseSpots().get(1).getTravelTimeMinutes()).isEqualTo(60);
        
        // 삭제 로직 안에서 남아있는 스팟(2개)에 대해 재계산이 일어나므로, API는 1번 호출됨
        verify(directionsClient, times(1)).getTravelTimeMinutes(anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    @DisplayName("코스 스팟 순서 변경 시 동선 순차적 재계산 검증")
    void updateSpotOrder_recalculatesTravelTime() {
        // given
        Course course = createCourseFixture();
        CourseSpot spot1 = createSpotFixture(course, 1L, 1, 1);
        CourseSpot spot2 = createSpotFixture(course, 2L, 1, 2);
        CourseSpot spot3 = createSpotFixture(course, 3L, 1, 3);
        course.getCourseSpots().addAll(new ArrayList<>(List.of(spot1, spot2, spot3)));

        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(directionsClient.getTravelTimeMinutes(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(20); // 순서 변경 후 각 이동시간 20분으로 Mocking

        // 역순(3 -> 2 -> 1)으로 순서 변경 요청
        CourseSpotOrderUpdateRequest request = new CourseSpotOrderUpdateRequest(List.of(3L, 2L, 1L));

        // when
        courseService.updateSpotOrder(1L, request);

        // then
        // 1번 순서가 된 기존 spot3
        // 2번 순서가 된 기존 spot2
        // 3번 순서가 된 기존 spot1
        assertThat(spot3.getSequenceOrder()).isEqualTo(1);
        assertThat(spot2.getSequenceOrder()).isEqualTo(2);
        assertThat(spot1.getSequenceOrder()).isEqualTo(3);

        // 재계산 결과, 첫 번째 스팟(spot3)은 null, 나머지는 20분이 되어야 함
        assertThat(spot3.getTravelTimeMinutes()).isNull();
        assertThat(spot2.getTravelTimeMinutes()).isEqualTo(20);
        assertThat(spot1.getTravelTimeMinutes()).isEqualTo(20);

        // 3개의 스팟에 대해 재계산을 수행하므로 0번째 제외 총 2번의 API 호출이 있어야 함
        verify(directionsClient, times(2)).getTravelTimeMinutes(anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    @DisplayName("외부 길찾기 API 장애 시 기본값(30분) 예외 처리 검증")
    void fallbackTravelTimeOnApiError() {
        // given
        Course course = createCourseFixture();
        CourseSpot spot1 = createSpotFixture(course, 1L, 1, 1);
        course.getCourseSpots().add(spot1);

        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(courseSpotRepository.save(any(CourseSpot.class))).thenAnswer(inv -> {
            CourseSpot saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 2L);
            return saved;
        });
        
        // 길찾기 API가 RuntimeException을 던지도록 설정
        when(directionsClient.getTravelTimeMinutes(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenThrow(new RuntimeException("카카오 길찾기 API 장애 발생!"));

        CourseSpotAddRequest request = new CourseSpotAddRequest(200L, 1, 2, "새 스팟");

        // when
        courseService.addCourseSpot(1L, request);

        // then
        assertThat(course.getCourseSpots()).hasSize(2);
        // 에러가 났어도 서버가 터지지 않고 Fallback 값인 30분이 저장되어야 함
        assertThat(course.getCourseSpots().get(1).getTravelTimeMinutes()).isEqualTo(30);
    }
}
