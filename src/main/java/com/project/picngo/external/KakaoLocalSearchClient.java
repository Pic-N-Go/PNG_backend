package com.project.picngo.external;

import com.project.picngo.external.dto.KakaoLocalSearchResponse;
import com.project.picngo.external.dto.PlaceSearchResult;
import com.project.picngo.spot.dto.Coordinate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Slf4j
@Component
public class KakaoLocalSearchClient {

    private static final int DEFAULT_RADIUS_METERS = 2000;

    private final WebClient webClient;
    private final String apiKey;

    public KakaoLocalSearchClient(
            WebClient.Builder webClientBuilder,
            @Value("${kakao.rest.api.key}") String apiKey) {

        this.webClient = webClientBuilder.baseUrl("https://dapi.kakao.com/v2/local/search/keyword.json").build();
        this.apiKey = apiKey;
    }

    /**
     * 스팟 주변 키워드(예: {스팟명} 주차장)로 카카오 지도 로컬 검색 API 호출.
     * 결과 0건과 호출 실패를 구분해서 반환한다. 호출부가 이를 구분해야
     * 일시적 장애로 스팟에 영구적인 보정 실패 딱지가 붙지 않는다.
     */
    public PlaceSearchResult searchNearbyPlace(String query, Double originLat, Double originLng, Integer radiusMeters) {
        if (query == null || query.isBlank()) {
            return PlaceSearchResult.notFound();
        }

        int radius = radiusMeters != null ? radiusMeters : DEFAULT_RADIUS_METERS;
        log.info("🔍 [카카오 지도 키워드 검색 요청] 쿼리: '{}', 기준: ({},{}), 반경: {}m",
                query, originLat, originLng, radius);

        KakaoLocalSearchResponse response;
        try {
            response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("query", query)
                            .queryParam("x", originLng)
                            .queryParam("y", originLat)
                            .queryParam("radius", radius)
                            .queryParam("sort", "distance")
                            .build())
                    .header("Authorization", "KakaoAK " + apiKey)
                    .retrieve()
                    .bodyToMono(KakaoLocalSearchResponse.class)
                    .block();
        } catch (WebClientResponseException e) {
            // 상태코드만으론 원인을 못 찾는다. 카카오는 응답 본문에 사유를 적어서 보낸다.
            log.warn("❌ 카카오 지도 키워드 검색 API 오류 (쿼리: {}): {} - 응답 본문: {}",
                    query, e.getStatusCode(), e.getResponseBodyAsString());
            return PlaceSearchResult.error();
        } catch (Exception e) {
            log.warn("❌ 카카오 지도 키워드 검색 호출 실패 (쿼리: {}): {}", query, e.toString());
            return PlaceSearchResult.error();
        }

        // 응답 본문이 없거나 documents 필드 자체가 없는 것은 "주차장이 없다"가 아니라 응답이 깨진 것이다.
        // NOT_FOUND로 처리하면 호출부가 RESOLVE_FAILED로 확정해 재보정을 영구히 막는다.
        if (response == null || response.documents() == null) {
            log.warn("❌ [카카오 지도 검색 응답 형식 오류] 쿼리: '{}', 응답: {}", query, response);
            return PlaceSearchResult.error();
        }

        if (response.documents().isEmpty()) {
            log.info("🔎 [카카오 지도 키워드 검색 결과 없음] 쿼리: '{}'", query);
            return PlaceSearchResult.notFound();
        }

        KakaoLocalSearchResponse.PlaceDocument doc = response.documents().get(0);
        if (doc == null || doc.x() == null || doc.y() == null) {
            // Double.parseDouble(null)은 NumberFormatException이 아니라 NPE를 던진다.
            log.warn("❌ [카카오 지도 검색 결과에 좌표 없음] 쿼리: '{}'", query);
            return PlaceSearchResult.error();
        }

        double latitude;
        double longitude;
        try {
            latitude = Double.parseDouble(doc.y());
            longitude = Double.parseDouble(doc.x());
        } catch (NumberFormatException e) {
            log.warn("❌ [카카오 지도 좌표 파싱 실패] 쿼리: '{}', x: {}, y: {}", query, doc.x(), doc.y());
            return PlaceSearchResult.error();
        }

        // parseDouble은 "NaN", "Infinity"를 예외 없이 통과시킨다.
        // 그대로 두면 거리 계산이 NaN이 되고, NaN 비교는 항상 false라 게이트에서 탈락해
        // 멀쩡한 스팟이 보정 불가로 확정된다.
        if (!isValidCoordinate(latitude, longitude)) {
            log.warn("❌ [카카오 지도 좌표 범위 오류] 쿼리: '{}', 위도: {}, 경도: {}", query, latitude, longitude);
            return PlaceSearchResult.error();
        }

        Coordinate place = new Coordinate(latitude, longitude, doc.placeName());
        log.info("🟢 [카카오 지도 키워드 검색 성공] 쿼리: '{}' -> 장소: '{}' (위도: {}, 경도: {})",
                query, place.name(), place.latitude(), place.longitude());
        return PlaceSearchResult.found(place);
    }

    private boolean isValidCoordinate(double latitude, double longitude) {
        return Double.isFinite(latitude) && Double.isFinite(longitude)
                && latitude >= -90.0 && latitude <= 90.0
                && longitude >= -180.0 && longitude <= 180.0;
    }
}
