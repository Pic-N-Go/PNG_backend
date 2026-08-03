package com.project.picngo.course.service;

import com.project.picngo.course.dto.CourseSpotResponse;
import com.project.picngo.course.dto.CourseSpotSyncItem;
import com.project.picngo.course.dto.CourseSpotSyncRequest;
import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.repository.SpotRepository;
import com.project.picngo.spot.service.SpotNavigationService;
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

    @Mock
    private SpotNavigationService spotNavigationService;

    @BeforeEach
    void setUp() {
        courseFacade = new CourseFacade(courseService, routeCacheService, spotRepository, spotNavigationService);
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

        CourseSpotResponse resp1 = new CourseSpotResponse(10L, 100L, "스팟1", 33.1, 126.1, null, List.of("명소"), "url", 5, 1, 1, "스팟1", null);
        CourseSpotResponse resp2 = new CourseSpotResponse(11L, 200L, "스팟2", 33.2, 126.2, null, List.of("명소"), "url", 5, 1, 2, "스팟2", null);

        when(courseService.getDaySpots(courseId, dayNumber)).thenReturn(List.of(resp1, resp2));
        
        Spot spot1 = createSpot(100L, 33.1, 126.1);
        Spot spot2 = createSpot(200L, 33.2, 126.2);
        when(spotRepository.findByIdIn(List.of(100L, 200L))).thenReturn(List.of(spot1, spot2));
        when(routeCacheService.getTravelInfoWithCache(33.1, 126.1, 33.2, 126.2)).thenReturn(new com.project.picngo.external.dto.DirectionsResponse(45, null, 0));

        // when
        courseFacade.syncCourseSpots(userId, courseId, request);

        // then
        verify(courseService).syncCourseSpots(userId, courseId, request);
        verify(routeCacheService).getTravelInfoWithCache(33.1, 126.1, 33.2, 126.2);
        
        @SuppressWarnings("unchecked")
        Map<Long, Integer> capturedUpdates = (Map<Long, Integer>) mockingDetails(courseService)
            .getInvocations().stream()
            .filter(inv -> inv.getMethod().getName().equals("updateTravelTimes"))
            .findFirst().get().getArgument(1);
            
        assert capturedUpdates.get(10L) == null;
        assert capturedUpdates.get(11L) == 45;
    }

    @Test
    @DisplayName("길찾기 API 102 에러 발생 시 온디맨드 보정 후 재계산 검증")
    void syncCourseSpots_handlesResultCode102_withCorrection() {
        // given
        Long userId = 1L;
        Long courseId = 1L;
        Integer dayNumber = 1;

        CourseSpotSyncItem item1 = new CourseSpotSyncItem(null, 100L, 1, 1, "성산일출봉");
        CourseSpotSyncItem item2 = new CourseSpotSyncItem(null, 200L, 1, 2, "함덕해수욕장");
        CourseSpotSyncRequest request = new CourseSpotSyncRequest(List.of(item1, item2));

        CourseSpotResponse resp1 = new CourseSpotResponse(10L, 100L, "성산일출봉", 33.458, 126.942, null, List.of("명소"), "url", 5, 1, 1, "성산일출봉", null);
        CourseSpotResponse resp2 = new CourseSpotResponse(11L, 200L, "함덕해수욕장", 33.543, 126.669, null, List.of("명소"), "url", 5, 1, 2, "함덕해수욕장", null);

        when(courseService.getDaySpots(courseId, dayNumber)).thenReturn(List.of(resp1, resp2));

        Spot spot1 = createSpot(100L, 33.458, 126.942);
        Spot spot2 = createSpot(200L, 33.543, 126.669);
        when(spotRepository.findByIdIn(List.of(100L, 200L))).thenReturn(List.of(spot1, spot2));

        // 1차 길찾기 시도: 102 에러
        when(routeCacheService.getTravelInfoWithCache(33.458, 126.942, 33.543, 126.669))
                .thenReturn(new com.project.picngo.external.dto.DirectionsResponse(null, null, 102));

        // 온디맨드 보정: 주차장 좌표 반환
        com.project.picngo.spot.dto.Coordinate parkingCoord = new com.project.picngo.spot.dto.Coordinate(33.462, 126.936, "성산일출봉 주차장");
        when(spotNavigationService.correctSpotNavigation(spot1)).thenReturn(parkingCoord);

        // 재시도 길찾기: 42분 반환
        when(routeCacheService.getTravelInfoWithCache(33.462, 126.936, 33.543, 126.669))
                .thenReturn(new com.project.picngo.external.dto.DirectionsResponse(42, null, 0));

        // when
        courseFacade.syncCourseSpots(userId, courseId, request);

        // then
        verify(spotNavigationService).correctSpotNavigation(spot1);
        verify(routeCacheService).getTravelInfoWithCache(33.462, 126.936, 33.543, 126.669);

        @SuppressWarnings("unchecked")
        Map<Long, Integer> capturedUpdates = (Map<Long, Integer>) mockingDetails(courseService)
                .getInvocations().stream()
                .filter(inv -> inv.getMethod().getName().equals("updateTravelTimes"))
                .findFirst().get().getArgument(1);

        assert capturedUpdates.get(11L) == 42;
    }

    @Test
    @DisplayName("보정 후 재계산까지 실패하면 원본이 아닌 보정 좌표로 Fallback 추정")
    void syncCourseSpots_fallbackUsesCorrectedCoordinate() {
        // given
        Long userId = 1L;
        Long courseId = 1L;
        Integer dayNumber = 1;

        CourseSpotSyncItem item1 = new CourseSpotSyncItem(null, 100L, 1, 1, "함덕해수욕장");
        CourseSpotSyncItem item2 = new CourseSpotSyncItem(null, 200L, 1, 2, "성산일출봉");
        CourseSpotSyncRequest request = new CourseSpotSyncRequest(List.of(item1, item2));

        CourseSpotResponse resp1 = new CourseSpotResponse(10L, 100L, "함덕해수욕장", 33.543, 126.669, null, List.of("명소"), "url", 5, 1, 1, "함덕해수욕장", null);
        CourseSpotResponse resp2 = new CourseSpotResponse(11L, 200L, "성산일출봉", 33.458, 126.942, null, List.of("명소"), "url", 5, 1, 2, "성산일출봉", null);

        when(courseService.getDaySpots(courseId, dayNumber)).thenReturn(List.of(resp1, resp2));

        Spot spot1 = createSpot(100L, 33.543, 126.669);
        Spot spot2 = createSpot(200L, 33.458, 126.942);
        when(spotRepository.findByIdIn(List.of(100L, 200L))).thenReturn(List.of(spot1, spot2));

        // 1차 길찾기: 103(도착지 비도로) 실패
        when(routeCacheService.getTravelInfoWithCache(33.543, 126.669, 33.458, 126.942))
                .thenReturn(new com.project.picngo.external.dto.DirectionsResponse(null, null, 103));

        // 도착지 보정 성공: 주차장 좌표 확보
        com.project.picngo.spot.dto.Coordinate parkingCoord =
                new com.project.picngo.spot.dto.Coordinate(33.462, 126.936, "성산일출봉 주차장");
        when(spotNavigationService.correctSpotNavigation(spot2)).thenReturn(parkingCoord);

        // 재계산도 실패 (카카오 장애 등) -> 최종 Fallback으로 넘어간다
        when(routeCacheService.getTravelInfoWithCache(33.543, 126.669, 33.462, 126.936))
                .thenReturn(null);
        when(routeCacheService.calculateFallbackTime(33.543, 126.669, 33.462, 126.936))
                .thenReturn(38);

        // when
        courseFacade.syncCourseSpots(userId, courseId, request);

        // then: 원본(33.458, 126.942)이 아닌 보정 좌표(33.462, 126.936)로 추정해야 한다
        verify(routeCacheService).calculateFallbackTime(33.543, 126.669, 33.462, 126.936);
        verify(routeCacheService, never()).calculateFallbackTime(33.543, 126.669, 33.458, 126.942);

        @SuppressWarnings("unchecked")
        Map<Long, Integer> capturedUpdates = (Map<Long, Integer>) mockingDetails(courseService)
                .getInvocations().stream()
                .filter(inv -> inv.getMethod().getName().equals("updateTravelTimes"))
                .findFirst().get().getArgument(1);

        assert capturedUpdates.get(11L) == 38;
    }
}
