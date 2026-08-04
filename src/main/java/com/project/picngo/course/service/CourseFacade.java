package com.project.picngo.course.service;

import com.project.picngo.course.dto.CourseSpotResponse;
import com.project.picngo.course.dto.CourseSpotSyncItem;
import com.project.picngo.course.dto.CourseSpotSyncRequest;
import com.project.picngo.course.dto.TravelTimeResult;
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
import java.util.Objects;
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

        Map<Long, TravelTimeResult> travelTimeUpdates = new HashMap<>();
        // 하루의 첫 스팟은 이전 스팟이 없어 이동시간이 존재하지 않는다
        travelTimeUpdates.put(daySpots.get(0).id(), TravelTimeResult.unknown());

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
                    // 보정을 "시도"했는지가 아니라 좌표가 실제로 "바뀌었는지"를 본다.
                    // correctSpotNavigation은 검색 실패 시에도 원본 좌표를 돌려주므로,
                    // 시도 여부로 판단하면 같은 좌표로 카카오를 한 번 더 부르고 같은 실패를 받는다.
                    boolean coordinateChanged = false;

                    // 102: 출발 지점 주변의 도로를 탐색할 수 없음
                    if (resultCode == 102 && s1.getAccessType() != AccessType.ROAD_ACCESSIBLE && s1.getAccessType() != AccessType.RESOLVE_FAILED) {
                        log.info("⚠️ [길찾기 실패 -> 온디맨드 출발지 보정 진입] s1({})", s1.getName());
                        Coordinate target = correctQuietly(s1);
                        if (isRelocated(c1, target)) {
                            updatedC1 = target;
                            coordinateChanged = true;
                        }
                    }

                    // 103: 도착 지점 주변의 도로를 탐색할 수 없음
                    if (resultCode == 103 && s2.getAccessType() != AccessType.ROAD_ACCESSIBLE && s2.getAccessType() != AccessType.RESOLVE_FAILED) {
                        log.info("⚠️ [길찾기 실패 -> 온디맨드 도착지 보정 진입] s2({})", s2.getName());
                        Coordinate target = correctQuietly(s2);
                        if (isRelocated(c2, target)) {
                            updatedC2 = target;
                            coordinateChanged = true;
                        }
                    }

                    if (coordinateChanged) {
                        DirectionsResponse retryRouteInfo = routeCacheService.getTravelInfoWithCache(
                                updatedC1.latitude(), updatedC1.longitude(),
                                updatedC2.latitude(), updatedC2.longitude()
                        );
                        travelTime = retryRouteInfo != null ? retryRouteInfo.travelTimeMinutes() : null;
                        log.info("✅ [온디맨드 보정 완료 재계산] ({}) naviTarget: ({},{}), ({}) naviTarget: ({},{}) => 재계산결과: {}분",
                                s1.getName(), updatedC1.latitude(), updatedC1.longitude(),
                                s2.getName(), updatedC2.latitude(), updatedC2.longitude(),
                                travelTime);
                    } else {
                        log.info("ℹ️ [보정 좌표 없음 - 재계산 생략] ({}) -> ({})", s1.getName(), s2.getName());
                    }
                }

                travelTimeUpdates.put(currentSpot.id(),
                        resolveTravelTime(travelTime, s1, s2, updatedC1, updatedC2));
            } else {
                log.warn("⚠️ [이동시간 산출 불가] 코스에 담긴 스팟을 찾을 수 없습니다. courseSpotId: {}, spotId: {}",
                        currentSpot.id(), currentSpot.spotId());
                travelTimeUpdates.put(currentSpot.id(), TravelTimeResult.unknown());
            }
        }

        courseService.updateTravelTimes(courseId, travelTimeUpdates);
    }

    /**
     * 진입점 보정은 이동시간 정확도를 높이기 위한 부가 작업이므로,
     * 실패하더라도 코스 저장 자체를 실패시키지 않는다.
     * 동시 요청이 같은 스팟의 대표 진입점을 저장하려다 유니크 제약에 걸리는 경우가 대표적이다.
     * 이때는 원본 좌표로 계산하고, 다음 요청이 이미 저장된 진입점을 찾아 쓰게 된다.
     */
    private Coordinate correctQuietly(Spot spot) {
        try {
            return spotNavigationService.correctSpotNavigation(spot);
        } catch (Exception e) {
            log.warn("⚠️ [진입점 보정 실패 - 원본 좌표로 계속 진행] spotId: {}, 사유: {}",
                    spot.getId(), e.toString());
            return null;
        }
    }

    /**
     * 보정 결과가 원래 좌표와 다른 지점을 가리키는지 여부.
     * 이름(라벨)은 경로 탐색에 영향이 없으므로 위경도만 비교한다.
     */
    private boolean isRelocated(Coordinate origin, Coordinate corrected) {
        if (corrected == null) {
            return false;
        }
        return !Objects.equals(origin.latitude(), corrected.latitude())
                || !Objects.equals(origin.longitude(), corrected.longitude());
    }

    /**
     * 카카오 실측값이 있으면 그대로, 없으면 자체 추정으로 대체하되 출처를 구분해 담는다.
     * 추정 계산조차 불가능하면(유효 범위 밖 좌표) 근거 없는 기본값 대신 값 없음으로 남긴다.
     */
    private TravelTimeResult resolveTravelTime(Integer measuredMinutes, Spot s1, Spot s2,
                                               Coordinate c1, Coordinate c2) {
        if (measuredMinutes != null) {
            return TravelTimeResult.measured(measuredMinutes);
        }

        Integer estimatedMinutes = routeCacheService.calculateFallbackTime(
                c1.latitude(), c1.longitude(),
                c2.latitude(), c2.longitude()
        );

        if (estimatedMinutes == null) {
            log.warn("⚠️ [이동시간 산출 불가] ({}) -> ({}) 좌표가 유효하지 않습니다", s1.getName(), s2.getName());
            return TravelTimeResult.unknown();
        }

        log.info("ℹ️ [최종 Fallback 적용] ({}) ({},{}) -> ({}) ({},{}) => 추정시간: {}분",
                s1.getName(), c1.latitude(), c1.longitude(),
                s2.getName(), c2.latitude(), c2.longitude(),
                estimatedMinutes);
        return TravelTimeResult.estimate(estimatedMinutes);
    }
}
