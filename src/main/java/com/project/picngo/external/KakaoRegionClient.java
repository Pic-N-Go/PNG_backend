package com.project.picngo.external;

import com.project.picngo.external.dto.KakaoRegionApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Component
public class KakaoRegionClient {

    private final WebClient webClient;
    private final String apiKey;

    public KakaoRegionClient(
            WebClient.Builder webClientBuilder,
            @Value("${kakao.rest.api.key}") String apiKey,
            @Value("${kakao.local.base-url:https://dapi.kakao.com}") String baseUrl) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
    }

    // 좌표 → 시/도 이름(region_1depth_name). 실패/빈 결과면 null.
    public String coord2region(Double lat, Double lng) {
        try {
            KakaoRegionApiResponse response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v2/local/geo/coord2regioncode.json")
                            .queryParam("x", lng)   // 카카오는 x=경도, y=위도
                            .queryParam("y", lat)
                            .build())
                    .header("Authorization", "KakaoAK " + apiKey)
                    .retrieve()
                    .bodyToMono(KakaoRegionApiResponse.class)
                    .block();

            if (response != null && response.documents() != null && !response.documents().isEmpty()) {
                return response.documents().get(0).region_1depth_name();
            }
        } catch (Exception e) {
            log.warn("카카오 역지오코딩 실패 lat={} lng={}: {}", lat, lng, e.getMessage());
        }
        return null;
    }
}
