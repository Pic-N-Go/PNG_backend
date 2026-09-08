package com.project.picngo.course.agent;

import com.project.picngo.course.agent.dto.SpotCandidate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * [워커 3: 동선 최적화 전문 에이전트]
 * LLM에게 산수나 거리 계산을 맡기지 않고, Java 코드로 Haversine 거리 계산 및
 * Nearest-Neighbor TSP 알고리즘을 수행하여 효율적인 출사 동선을 도출합니다.
 */
@Slf4j
@Component
public class RouteOptimizerAgent {

    private static final double EARTH_RADIUS_KM = 6371.0;
    private static final double AVERAGE_SPEED_KMH = 30.0; // 도심/관광지 평균 주행속도

    public record OptimizedRoute(
            List<SpotCandidate> orderedSpots,
            List<Integer> travelMinutesList
    ) {}

    public OptimizedRoute optimizeRoute(List<SpotCandidate> candidates, int targetCount) {
        if (candidates == null || candidates.isEmpty()) {
            return new OptimizedRoute(List.of(), List.of());
        }

        if (candidates.size() <= targetCount) {
            return buildRouteWithTravelTimes(candidates);
        }

        // Greedy Nearest-Neighbor: 첫 번째 스팟(가장 인기 있는 스팟)을 시작점으로 가장 가까운 스팟을 순서대로 선택
        List<SpotCandidate> remaining = new ArrayList<>(candidates);
        List<SpotCandidate> ordered = new ArrayList<>();

        SpotCandidate current = remaining.remove(0);
        ordered.add(current);

        while (ordered.size() < targetCount && !remaining.isEmpty()) {
            SpotCandidate next = null;
            double minDistance = Double.MAX_VALUE;

            for (SpotCandidate candidate : remaining) {
                double dist = calculateDistanceKm(
                        current.latitude(), current.longitude(),
                        candidate.latitude(), candidate.longitude()
                );
                if (dist < minDistance) {
                    minDistance = dist;
                    next = candidate;
                }
            }

            if (next != null) {
                remaining.remove(next);
                ordered.add(next);
                current = next;
            } else {
                break;
            }
        }

        return buildRouteWithTravelTimes(ordered);
    }

    private OptimizedRoute buildRouteWithTravelTimes(List<SpotCandidate> spots) {
        List<Integer> travelTimes = new ArrayList<>();
        travelTimes.add(0); // 첫 번째 스팟은 이동 시간 0분

        for (int i = 0; i < spots.size() - 1; i++) {
            SpotCandidate from = spots.get(i);
            SpotCandidate to = spots.get(i + 1);

            double dist = calculateDistanceKm(
                    from.latitude(), from.longitude(),
                    to.latitude(), to.longitude()
            );

            // 거리 기반 이동 시간 추정 (기본 10분 ~ 최대 60분)
            int minutes = (int) Math.round((dist / AVERAGE_SPEED_KMH) * 60.0);
            minutes = Math.max(10, Math.min(minutes, 60));
            travelTimes.add(minutes);
        }

        return new OptimizedRoute(spots, travelTimes);
    }

    public static double calculateDistanceKm(Double lat1, Double lon1, Double lat2, Double lon2) {
        if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) {
            return 10.0;
        }

        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }
}
