package com.project.picngo.external;

import com.project.picngo.external.dto.KakaoLocalSearchResponse;
import com.project.picngo.spot.dto.Coordinate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Component
public class KakaoLocalSearchClient {

    private final WebClient webClient;
    private final String apiKey;

    public KakaoLocalSearchClient(
            WebClient.Builder webClientBuilder,
            @Value("${kakao.rest.api.key}") String apiKey) {
        
        this.webClient = webClientBuilder.baseUrl("https://dapi.kakao.com/v2/local/search/keyword.json").build();
        this.apiKey = apiKey;
    }

    /**
     * 스팟 주변 키워드(예: {스팟명} 주차장)로 카카오 지도 로컬 검색 API 호출
     */
    public Coordinate searchNearbyPlace(String query, Double originLat, Double originLng, Integer radiusMeters) {
        if (query == null || query.isBlank()) return null;

        try {
            KakaoLocalSearchResponse response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("query", query)
                            .queryParam("x", originLng)
                            .queryParam("y", originLat)
                            .queryParam("radius", radiusMeters != null ? radiusMeters : 2000)
                            .queryParam("sort", "distance")
                            .build())
                    .header("Authorization", "KakaoAK " + apiKey)
                    .retrieve()
                    .bodyToMono(KakaoLocalSearchResponse.class)
                    .block();

            if (response != null && response.documents() != null && !response.documents().isEmpty()) {
                KakaoLocalSearchResponse.PlaceDocument doc = response.documents().get(0);
                Double lat = Double.parseDouble(doc.y());
                Double lng = Double.parseDouble(doc.x());
                String placeName = doc.placeName();

                log.info("🟢 [카카오 지도 키워드 검색 성공] 쿼리: '{}' -> 장소: '{}' (위도: {}, 경도: {})", query, placeName, lat, lng);
                return new Coordinate(lat, lng, placeName);
            }
        } catch (Exception e) {
            log.warn("❌ 카카오 지도 키워드 검색 API 호출 실패 (쿼리: {}): {}", query, e.getMessage());
        }
        return null;
    }
}
