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
        SpotAccessPoint ap = spot.getPrimaryAccessPoint();
        if (ap != null) {
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

        // 4. 거리 게이트까지 통과한 후보가 있으면 저장
        if (searchResult.isFound()) {
            Coordinate candidate = searchResult.place();

            SpotAccessPoint accessPoint = SpotAccessPoint.builder()
                    .spot(spot)
                    .latitude(candidate.latitude())
                    .longitude(candidate.longitude())
                    .label(candidate.name())
                    .source(AccessPointSource.KAKAO_LOCAL)
                    .build();

            spotAccessPointRepository.save(accessPoint);
            spot.addAccessPoint(accessPoint);
            spot.assignPrimaryAccessPoint(accessPoint);
            spot.updateAccessType(AccessType.NEEDS_ENTRANCE);
            spotRepository.save(spot);

            return candidate;
        }

        // 5. 검색은 정상이었는데 쓸 만한 후보가 없는 경우에만 실패로 확정한다.
        //    재시도해도 결과가 달라지지 않으므로 다음부터는 탐색을 건너뛴다.
        log.warn("⚠️ [주차장 보정 탐색 실패 처리 - RESOLVE_FAILED] spotId: {}", spot.getId());
        spot.updateAccessType(AccessType.RESOLVE_FAILED);
        spotRepository.save(spot);
        return originOf(spot);
    }

    /**
     * 검색어를 우선순위대로 시도하며, 거리 게이트까지 통과한 후보만 FOUND로 돌려준다.
     * 게이트를 검색 루프 안에서 적용해야 "주차장"이 엉뚱한 곳을 물어왔을 때
     * "매표소"로 넘어갈 수 있다. 밖에서 검증하면 첫 후보가 탈락하는 순간 탐색이 끝난다.
     *
     * 호출 실패(ERROR)가 나면 남은 검색어를 태우지 않고 즉시 중단한다.
     * 같은 원인으로 실패할 가능성이 높고, 재시도 여지를 남겨야 하기 때문이다.
     */
    private PlaceSearchResult searchAccessPoint(Spot spot) {
        for (String suffix : SEARCH_SUFFIXES) {
            String query = spot.getName() + suffix;
            PlaceSearchResult result = kakaoLocalSearchClient.searchNearbyPlace(
                    query,
                    spot.getLatitude(),
                    spot.getLongitude(),
                    SEARCH_RADIUS_METERS);

            if (result.isError()) {
                return result;
            }

            if (result.isFound() && passesDistanceGate(spot, query, result.place())) {
                return result;
            }
        }
        return PlaceSearchResult.notFound();
    }

    /**
     * 검색 결과가 원본 스팟에서 2km 이내인지 확인한다.
     * 동명의 다른 지역 주차장(예: 반대편 탐방로)이 걸리는 것을 막는다.
     */
    private boolean passesDistanceGate(Spot spot, String query, Coordinate candidate) {
        double distanceMeters = calculateHaversineDistanceMeters(
                spot.getLatitude(), spot.getLongitude(),
                candidate.latitude(), candidate.longitude());

        if (distanceMeters <= MAX_DISTANCE_METERS) {
            log.info("✨ [거리 게이트 통과] spotId: {}, 쿼리: '{}', 후보: {}, 거리: {}m",
                    spot.getId(), query, candidate.name(), Math.round(distanceMeters));
            return true;
        }

        log.warn("❌ [거리 게이트 탈락 - 다음 검색어로] spotId: {}, 쿼리: '{}', 후보: {}, 거리: {}m",
                spot.getId(), query, candidate.name(), Math.round(distanceMeters));
        return false;
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
