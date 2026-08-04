package com.project.picngo.course.service;

import com.project.picngo.course.dto.CourseSpotResponse;
import com.project.picngo.course.dto.CourseSpotSyncItem;
import com.project.picngo.course.dto.CourseSpotSyncRequest;
import com.project.picngo.external.dto.DirectionsResponse;
import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.domain.enums.AccessType;
import com.project.picngo.spot.dto.Coordinate;
import com.project.picngo.spot.repository.SpotRepository;
import com.project.picngo.spot.service.SpotNavigationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourseFacade {

    private final CourseService courseService;
    private final RouteCacheService routeCacheService;
    private final SpotRepository spotRepository;
    private final SpotNavigationService spotNavigationService;

    public void syncCourseSpots(Long userId, Long courseId, CourseSpotSyncRequest request) {
        courseService.syncCourseSpots(userId, courseId, request);

        // request에 포함된 고유한 dayNumber 추출하여 각각 경로 재계산
        request.spots().stream()
                .map(CourseSpotSyncItem::dayNumber)
                .distinct()
                .forEach(day -> recalculateTravelTimesForDay(courseId, day));
    }

    private void recalculateTravelTimesForDay(Long courseId, Integer dayNumber) {
        List<CourseSpotResponse> daySpots = courseService.getDaySpots(courseId, dayNumber);

        if (daySpots.isEmpty()) return;

        Map<Long, Integer> travelTimeUpdates = new HashMap<>();
        travelTimeUpdates.put(daySpots.get(0).id(), null);

        if (daySpots.size() == 1) {
            courseService.updateTravelTimes(courseId, travelTimeUpdates);
            return;
        }

        List<Long> spotIds = daySpots.stream().map(CourseSpotResponse::spotId).toList();
        Map<Long, Spot> spotMap = spotRepository.findByIdIn(spotIds).stream()
                .collect(Collectors.toMap(Spot::getId, s -> s));

        for (int i = 1; i < daySpots.size(); i++) {
            CourseSpotResponse currentSpot = daySpots.get(i);
            CourseSpotResponse prevSpot = daySpots.get(i - 1);

            Spot s1 = spotMap.get(prevSpot.spotId());
            Spot s2 = spotMap.get(currentSpot.spotId());

            if (s1 != null && s2 != null) {
                Coordinate c1 = s1.getNavigationTarget();
                Coordinate c2 = s2.getNavigationTarget();

                DirectionsResponse routeInfo = routeCacheService.getTravelInfoWithCache(
                        c1.latitude(), c1.longitude(),
                        c2.latitude(), c2.longitude()
                );

                Integer travelTime = routeInfo != null ? routeInfo.travelTimeMinutes() : null;
                Integer resultCode = routeInfo != null ? routeInfo.resultCode() : null;

                log.info("📌 [코스 스팟 이동시간 계산] ({})[{}] ({},{}) -> ({})[{}] ({},{}) => 1차 계산결과: {}분, resultCode: {}",
                        s1.getName(), s1.getAccessType(), c1.latitude(), c1.longitude(),
                        s2.getName(), s2.getAccessType(), c2.latitude(), c2.longitude(),
                        travelTime, resultCode);

                // 실제로 안내에 쓰는 좌표. 보정 결과를 최종 Fallback까지 이어서 쓰려고 블록 밖에 선언한다.
                // 보정에 성공했는데 재계산이 실패했을 때 추정 계산이 원본 좌표로 돌아가면,
                // 마지막 구간이 차량 시간과 별도로 붙는 도보 시간에 이중으로 계산된다.
                Coordinate updatedC1 = c1;
                Coordinate updatedC2 = c2;

                // 온디맨드 보정: 길찾기 실패 시 출발지(s1: 102) 또는 목적지(s2: 103)에 대해 주차장 보정 및 재계산
                if (travelTime == null && resultCode != null) {
                    boolean s1Corrected = false;
                    boolean s2Corrected = false;

                    // 102: 출발 지점 주변의 도로를 탐색할 수 없음
                    if (resultCode == 102 && s1.getAccessType() != AccessType.ROAD_ACCESSIBLE && s1.getAccessType() != AccessType.RESOLVE_FAILED) {
                        log.info("⚠️ [길찾기 실패 -> 온디맨드 출발지 보정 진입] s1({})", s1.getName());
                        Coordinate target = spotNavigationService.correctSpotNavigation(s1);
                        if (target != null) {
                            updatedC1 = target;
                        }
                        s1Corrected = true;
                    }

                    // 103: 도착 지점 주변의 도로를 탐색할 수 없음
                    if (resultCode == 103 && s2.getAccessType() != AccessType.ROAD_ACCESSIBLE && s2.getAccessType() != AccessType.RESOLVE_FAILED) {
                        log.info("⚠️ [길찾기 실패 -> 온디맨드 도착지 보정 진입] s2({})", s2.getName());
                        Coordinate target = spotNavigationService.correctSpotNavigation(s2);
                        if (target != null) {
                            updatedC2 = target;
                        }
                        s2Corrected = true;
                    }

                    if (s1Corrected || s2Corrected) {
                        DirectionsResponse retryRouteInfo = routeCacheService.getTravelInfoWithCache(
                                updatedC1.latitude(), updatedC1.longitude(),
                                updatedC2.latitude(), updatedC2.longitude()
                        );
                        travelTime = retryRouteInfo != null ? retryRouteInfo.travelTimeMinutes() : null;
                        log.info("✅ [온디맨드 보정 완료 재계산] ({}) naviTarget: ({},{}), ({}) naviTarget: ({},{}) => 재계산결과: {}분",
                                s1.getName(), updatedC1.latitude(), updatedC1.longitude(),
                                s2.getName(), updatedC2.latitude(), updatedC2.longitude(),
                                travelTime);
                    }
                }

                // 만약 끝까지 실패했다면, 최종적으로 자체 Fallback 계산 적용
                if (travelTime == null) {
                    travelTime = routeCacheService.calculateFallbackTime(
                            updatedC1.latitude(), updatedC1.longitude(),
                            updatedC2.latitude(), updatedC2.longitude()
                    );
                    log.info("ℹ️ [최종 Fallback 적용] ({}) ({},{}) -> ({}) ({},{}) => 추정시간: {}분",
                            s1.getName(), updatedC1.latitude(), updatedC1.longitude(),
                            s2.getName(), updatedC2.latitude(), updatedC2.longitude(),
                            travelTime);
                }

                // 추정 계산조차 불가능한 경우(유효 범위 밖 좌표)는 null로 남긴다.
                // 근거 없는 기본값을 넣으면 카카오 실측값과 구분되지 않은 채 일정에 반영된다.
                if (travelTime == null) {
                    log.warn("⚠️ [이동시간 산출 불가] ({}) -> ({}) 좌표가 유효하지 않아 null로 저장합니다",
                            s1.getName(), s2.getName());
                }
                travelTimeUpdates.put(currentSpot.id(), travelTime);
            } else {
                log.warn("⚠️ [이동시간 산출 불가] 코스에 담긴 스팟을 찾을 수 없습니다. courseSpotId: {}, spotId: {}",
                        currentSpot.id(), currentSpot.spotId());
                travelTimeUpdates.put(currentSpot.id(), null);
            }
        }

        courseService.updateTravelTimes(courseId, travelTimeUpdates);
    }
}
