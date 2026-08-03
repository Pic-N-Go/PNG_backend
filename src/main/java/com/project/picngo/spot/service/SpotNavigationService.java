package com.project.picngo.spot.service;

import com.project.picngo.external.KakaoLocalSearchClient;
import com.project.picngo.external.dto.PlaceSearchResult;
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

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpotNavigationService {

    /** 스팟명 뒤에 붙여 순서대로 시도할 검색어. 앞쪽이 우선순위가 높다 */
    private static final List<String> SEARCH_SUFFIXES = List.of(" 주차장", " 매표소");
    private static final int SEARCH_RADIUS_METERS = 2000;
    private static final double MAX_DISTANCE_METERS = 2000.0;

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
            return originOf(spot);
        }

        log.warn("⚠️ [비도로 스팟 탐색 보정 시작] spotId: {}, 스팟명: {}", spot.getId(), spot.getName());

        // 2. "{스팟명} 주차장" -> "{스팟명} 매표소" 순으로 탐색
        PlaceSearchResult searchResult = searchAccessPoint(spot);

        // 3. 호출 자체가 실패한 경우에는 상태를 바꾸지 않는다.
        //    API 권한 오류나 일시적 장애로 RESOLVE_FAILED가 박히면 원인을 고쳐도
        //    아래 2번 가드에 막혀 영원히 재시도되지 않는다(복구 수단이 DB 수정뿐이다).
        if (searchResult.isError()) {
            log.warn("⚠️ [검색 호출 실패 - 상태 유지하고 다음 기회에 재시도] spotId: {}, 현재 상태: {}",
                    spot.getId(), spot.getAccessType());
            return originOf(spot);
        }

        // 4. 2km 거리 검증 게이트 통과 여부 확인
        if (searchResult.isFound()) {
            Coordinate candidate = searchResult.place();
            double distanceMeters = calculateHaversineDistanceMeters(
                    spot.getLatitude(), spot.getLongitude(),
                    candidate.latitude(), candidate.longitude());

            if (distanceMeters <= MAX_DISTANCE_METERS) {
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
            }

            log.warn("❌ [검색된 주차장이 2km 초과로 무효 처리] spotId: {}, 거리: {}m",
                    spot.getId(), Math.round(distanceMeters));
        }

        // 5. 검색은 정상이었는데 후보가 없거나 게이트에서 탈락한 경우에만 실패로 확정한다.
        //    재시도해도 결과가 달라지지 않으므로 다음부터는 탐색을 건너뛴다.
        log.warn("⚠️ [주차장 보정 탐색 실패 처리 - RESOLVE_FAILED] spotId: {}", spot.getId());
        spot.updateAccessType(AccessType.RESOLVE_FAILED);
        spotRepository.save(spot);
        return originOf(spot);
    }

    /**
     * 검색어를 우선순위대로 시도한다.
     * 호출 실패(ERROR)가 나면 남은 검색어를 태우지 않고 즉시 중단한다.
     * 같은 원인으로 실패할 가능성이 높고, 재시도 여지를 남겨야 하기 때문이다.
     */
    private PlaceSearchResult searchAccessPoint(Spot spot) {
        for (String suffix : SEARCH_SUFFIXES) {
            PlaceSearchResult result = kakaoLocalSearchClient.searchNearbyPlace(
                    spot.getName() + suffix,
                    spot.getLatitude(),
                    spot.getLongitude(),
                    SEARCH_RADIUS_METERS);

            if (result.isFound() || result.isError()) {
                return result;
            }
        }
        return PlaceSearchResult.notFound();
    }

    private Coordinate originOf(Spot spot) {
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
