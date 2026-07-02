package com.project.picngo.course.service;

import com.project.picngo.course.domain.Course;
import com.project.picngo.course.domain.CourseSpot;
import com.project.picngo.course.dto.CourseSpotAddRequest;
import com.project.picngo.course.dto.CourseSpotOrderUpdateRequest;
import com.project.picngo.course.dto.CourseSpotResponse;
import com.project.picngo.course.repository.CourseChecklistRepository;
import com.project.picngo.course.repository.CourseRepository;
import com.project.picngo.course.repository.CourseSpotRepository;
import com.project.picngo.external.DirectionsClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
class CourseFacadeTest {

    private CourseService courseService;
    private CourseFacade courseFacade;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private CourseSpotRepository courseSpotRepository;

    @Mock
    private CourseChecklistRepository courseChecklistRepository;

    @Mock
    private DirectionsClient directionsClient;

    @BeforeEach
    void setUp() {
        courseService = new CourseService(courseRepository, courseSpotRepository, courseChecklistRepository);
        courseFacade = new CourseFacade(courseService, directionsClient);
    }

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
                .thenReturn(45);

        CourseSpotAddRequest request = new CourseSpotAddRequest(200L, 1, 2, "두번째 스팟");

        // when
        CourseSpotResponse response = courseFacade.addCourseSpot(1L, request);

        // then
        assertThat(course.getCourseSpots()).hasSize(2);
        assertThat(course.getCourseSpots().get(0).getTravelTimeMinutes()).isNull();
        assertThat(course.getCourseSpots().get(1).getTravelTimeMinutes()).isEqualTo(45);
        
        verify(directionsClient, times(1)).getTravelTimeMinutes(anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    @DisplayName("코스 스팟 중간 삽입 시 기존 스팟 순서(Shift) 검증")
    void addCourseSpot_shiftsExistingOrders() {
        // given
        Course course = createCourseFixture();
        CourseSpot spot1 = createSpotFixture(course, 1L, 1, 1);
        CourseSpot spot2 = createSpotFixture(course, 2L, 1, 2);
        course.getCourseSpots().addAll(new ArrayList<>(List.of(spot1, spot2)));

        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(courseSpotRepository.save(any(CourseSpot.class))).thenAnswer(inv -> {
            CourseSpot saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 3L);
            return saved;
        });
        
        // 1번 순서로 새 스팟을 삽입 (기존 1->2, 2->3 으로 밀려야 함)
        CourseSpotAddRequest request = new CourseSpotAddRequest(300L, 1, 1, "중간 삽입 스팟");

        // when
        courseFacade.addCourseSpot(1L, request);

        // then
        assertThat(course.getCourseSpots()).hasSize(3);
        
        // 기존 스팟들의 순서가 +1씩 밀렸는지 검증
        assertThat(spot1.getSequenceOrder()).isEqualTo(2);
        assertThat(spot2.getSequenceOrder()).isEqualTo(3);
        
        // 새 스팟은 1번으로 들어갔는지 검증
        CourseSpot newSpot = course.getCourseSpots().stream()
                .filter(s -> s.getId().equals(3L))
                .findFirst()
                .get();
        assertThat(newSpot.getSequenceOrder()).isEqualTo(1);
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
                .thenReturn(60);

        // when
        courseFacade.removeCourseSpot(1L, 2L);

        // then
        assertThat(course.getCourseSpots()).hasSize(2);
        verify(courseSpotRepository).delete(spot2);

        assertThat(course.getCourseSpots().get(0).getId()).isEqualTo(1L);
        assertThat(course.getCourseSpots().get(0).getTravelTimeMinutes()).isNull();

        assertThat(course.getCourseSpots().get(1).getId()).isEqualTo(3L);
        assertThat(course.getCourseSpots().get(1).getTravelTimeMinutes()).isEqualTo(60);
        
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
                .thenReturn(20);

        CourseSpotOrderUpdateRequest request = new CourseSpotOrderUpdateRequest(List.of(3L, 2L, 1L));

        // when
        courseFacade.updateSpotOrder(1L, request);

        // then
        assertThat(spot3.getSequenceOrder()).isEqualTo(1);
        assertThat(spot2.getSequenceOrder()).isEqualTo(2);
        assertThat(spot1.getSequenceOrder()).isEqualTo(3);

        assertThat(spot3.getTravelTimeMinutes()).isNull();
        assertThat(spot2.getTravelTimeMinutes()).isEqualTo(20);
        assertThat(spot1.getTravelTimeMinutes()).isEqualTo(20);

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
        
        when(directionsClient.getTravelTimeMinutes(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenThrow(new RuntimeException("카카오 길찾기 API 장애 발생!"));

        CourseSpotAddRequest request = new CourseSpotAddRequest(200L, 1, 2, "새 스팟");

        // when
        courseFacade.addCourseSpot(1L, request);

        // then
        assertThat(course.getCourseSpots()).hasSize(2);
        assertThat(course.getCourseSpots().get(1).getTravelTimeMinutes()).isEqualTo(30);
    }
}
