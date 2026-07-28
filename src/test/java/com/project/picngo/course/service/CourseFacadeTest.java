package com.project.picngo.course.service;

import com.project.picngo.course.dto.CourseSpotResponse;
import com.project.picngo.course.dto.CourseSpotSyncItem;
import com.project.picngo.course.dto.CourseSpotSyncRequest;
import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.repository.SpotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CourseFacadeTest {

    private CourseFacade courseFacade;

    @Mock
    private CourseService courseService;

    @Mock
    private RouteCacheService routeCacheService;

    @Mock
    private SpotRepository spotRepository;

    @BeforeEach
    void setUp() {
        courseFacade = new CourseFacade(courseService, routeCacheService, spotRepository);
    }

    private Spot createSpot(Long id, double lat, double lng) {
        Spot spot = Spot.builder().build();
        ReflectionTestUtils.setField(spot, "id", id);
        ReflectionTestUtils.setField(spot, "latitude", lat);
        ReflectionTestUtils.setField(spot, "longitude", lng);
        return spot;
    }

    @Test
    @DisplayName("코스 스팟 동기화 후 동선 재계산 호출 검증")
    void syncCourseSpots_recalculatesTravelTime() {
        // given
        Long userId = 1L;
        Long courseId = 1L;
        Integer dayNumber = 1;

        CourseSpotSyncItem item1 = new CourseSpotSyncItem(null, 100L, 1, 1, "스팟1");
        CourseSpotSyncItem item2 = new CourseSpotSyncItem(null, 200L, 1, 2, "스팟2");
        CourseSpotSyncRequest request = new CourseSpotSyncRequest(List.of(item1, item2));

        CourseSpotResponse resp1 = new CourseSpotResponse(10L, 100L, "스팟1", 33.1, 126.1, List.of("명소"), "url", 5, 1, 1, "스팟1", null);
        CourseSpotResponse resp2 = new CourseSpotResponse(11L, 200L, "스팟2", 33.2, 126.2, List.of("명소"), "url", 5, 1, 2, "스팟2", null);

        when(courseService.getDaySpots(courseId, dayNumber)).thenReturn(List.of(resp1, resp2));
        
        Spot spot1 = createSpot(100L, 33.1, 126.1);
        Spot spot2 = createSpot(200L, 33.2, 126.2);
        when(spotRepository.findByIdIn(List.of(100L, 200L))).thenReturn(List.of(spot1, spot2));
        when(routeCacheService.getTravelTimeMinutes(33.1, 126.1, 33.2, 126.2)).thenReturn(45);

        // when
        courseFacade.syncCourseSpots(userId, courseId, request);

        // then
        verify(courseService).syncCourseSpots(userId, courseId, request);
        verify(routeCacheService).getTravelTimeMinutes(33.1, 126.1, 33.2, 126.2);
        
        @SuppressWarnings("unchecked")
        Map<Long, Integer> capturedUpdates = (Map<Long, Integer>) mockingDetails(courseService)
            .getInvocations().stream()
            .filter(inv -> inv.getMethod().getName().equals("updateTravelTimes"))
            .findFirst().get().getArgument(1);
            
        assert capturedUpdates.get(10L) == null;
        assert capturedUpdates.get(11L) == 45;
    }
}
