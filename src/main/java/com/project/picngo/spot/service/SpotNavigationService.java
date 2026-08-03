package com.project.picngo.spot.service;

import com.project.picngo.external.KakaoLocalSearchClient;
import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.domain.SpotAccessPoint;
import com.project.picngo.spot.domain.enums.AccessPointSource;
import com.project.picngo.spot.domain.enums.AccessType;
import com.project.picngo.spot.dto.Coordinate;
import com.project.picngo.spot.repository.SpotAccessPointRepository;
import com.project.picngo.spot.repository.SpotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpotNavigationService {

    private final KakaoLocalSearchClient kakaoLocalSearchClient;
    private final SpotAccessPointRepository spotAccessPointRepository;
    private final SpotRepository spotRepository;

    /**
     * 카카오 길찾기에서 비도로 탐색 실패(result_code == 102/103)가 감지되었을 때 온디맨드로 주차장 좌표를 탐색 및 보정
     */
    @Transactional
    public Coordinate correctSpotNavigation(Spot spot) {
        if (spot == null) return null;

        // 1. 이미 등록된 유효한 대표 주차장/진입점(SpotAccessPoint)이 존재하는지 확인
        Optional<SpotAccessPoint> existingAp = spotAccessPointRepository.findBySpotIdAndIsPrimaryTrue(spot.getId());
        if (existingAp.isPresent()) {
            SpotAccessPoint ap = existingAp.get();
            log.info("🟢 [기존 보정 주차장 좌표 활용] spotId: {}, 주차장명: {} ({},{})",
                    spot.getId(), ap.getLabel(), ap.getLatitude(), ap.getLongitude());
            if (spot.getAccessType() != AccessType.NEEDS_ENTRANCE) {
                spot.updateAccessType(AccessType.NEEDS_ENTRANCE);
                spotRepository.save(spot);
            }
            return new Coordinate(ap.getLatitude(), ap.getLongitude(), ap.getLabel());
        }

        if (spot.getAccessType() == AccessType.RESOLVE_FAILED || spot.getAccessType() == AccessType.ROAD_ACCESSIBLE) {
            log.info("ℹ️ [이미 처리 완료된 스팟 - 추가 탐색 스킵] spotId: {}, status: {}", spot.getId(), spot.getAccessType());
            return new Coordinate(spot.getLatitude(), spot.getLongitude(), spot.getName());
        }

        log.warn("⚠️ [비도로 스팟 탐색 보정 시작] spotId: {}, 스팟명: {}", spot.getId(), spot.getName());

        // 2. 1차 시도: "{스팟명} 주차장" 검색
        String query = spot.getName() + " 주차장";
        Coordinate candidate = kakaoLocalSearchClient.searchNearbyPlace(query, spot.getLatitude(), spot.getLongitude(), 2000);

        // 3. 2차 시도: 1차 실패 시 "{스팟명} 매표소" 검색
        if (candidate == null) {
            query = spot.getName() + " 매표소";
            candidate = kakaoLocalSearchClient.searchNearbyPlace(query, spot.getLatitude(), spot.getLongitude(), 2000);
        }

        // 4. 2km 거리 검증 게이트 통과 여부 확인
        if (candidate != null) {
            double distanceMeters = calculateHaversineDistanceMeters(
                    spot.getLatitude(), spot.getLongitude(),
                    candidate.latitude(), candidate.longitude());

            if (distanceMeters <= 2000.0) {
                log.info("✨ [보정 주차장 2km 거리 게이트 통과] spotId: {}, 주차장명: {}, 거리: {}m",
                        spot.getId(), candidate.name(), Math.round(distanceMeters));

                SpotAccessPoint accessPoint = SpotAccessPoint.builder()
                        .spot(spot)
                        .latitude(candidate.latitude())
                        .longitude(candidate.longitude())
                        .label(candidate.name())
                        .source(AccessPointSource.KAKAO_LOCAL)
                        .isPrimary(true)
                        .build();

                spotAccessPointRepository.save(accessPoint);
                spot.addAccessPoint(accessPoint);
                spot.updateAccessType(AccessType.NEEDS_ENTRANCE);
                spotRepository.save(spot);

                return candidate;
            } else {
                log.warn("❌ [검색된 주차장이 2km 초과로 무효 처리] 거리: {}m", Math.round(distanceMeters));
            }
        }

        // 5. 주차장 검색 실패 시 RESOLVE_FAILED 상태 저장하여 재시도 방지
        log.warn("⚠️ [주차장 보정 탐색 실패 처리 - RESOLVE_FAILED] spotId: {}", spot.getId());
        spot.updateAccessType(AccessType.RESOLVE_FAILED);
        spotRepository.save(spot);
        return new Coordinate(spot.getLatitude(), spot.getLongitude(), spot.getName());
    }

    private double calculateHaversineDistanceMeters(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371000; // 지구 반지름 (m)
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}
