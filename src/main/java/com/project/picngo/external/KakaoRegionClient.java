package com.project.picngo.external;

import com.project.picngo.external.dto.KakaoRegionApiResponse;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.function.Supplier;

@Slf4j
@Component
public class KakaoRegionClient {

    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(3);

    private final WebClient webClient;
    private final String apiKey;
    private final CircuitBreaker circuitBreaker;

    public KakaoRegionClient(
            WebClient.Builder webClientBuilder,
            CircuitBreakerRegistry circuitBreakerRegistry,
            @Value("${kakao.rest.api.key}") String apiKey,
            @Value("${kakao.local.base-url:https://dapi.kakao.com}") String baseUrl) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
        // 로컬 검색과 같은 dapi.kakao.com이지만 서비스(역지오코딩)와 사용 맥락이 달라
        // 서킷을 분리한다. 한쪽 쿼터 소진이나 장애가 다른 쪽을 막지 않도록 하기 위함이다.
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("kakaoRegion");
    }

    // 좌표 → 시/도 이름(region_1depth_name). 실패/빈 결과면 null.
    public String coord2region(Double lat, Double lng) {
        try {
            Supplier<KakaoRegionApiResponse> call = CircuitBreaker.decorateSupplier(circuitBreaker, () ->
                    webClient.get()
                            .uri(uriBuilder -> uriBuilder
                                    .path("/v2/local/geo/coord2regioncode.json")
                                    .queryParam("x", lng)   // 카카오는 x=경도, y=위도
                                    .queryParam("y", lat)
                                    .build())
                            .header("Authorization", "KakaoAK " + apiKey)
                            .retrieve()
                            .bodyToMono(KakaoRegionApiResponse.class)
                            .timeout(CALL_TIMEOUT)
                            .block());

            KakaoRegionApiResponse response = call.get();

            if (response != null && response.documents() != null && !response.documents().isEmpty()) {
                return response.documents().get(0).region_1depth_name();
            }
        } catch (CallNotPermittedException e) {
            // 원래도 실패 시 null을 돌려주던 구조라 서킷 open도 같은 폴백을 탄다.
            log.warn("⚡ [카카오 역지오코딩 서킷 open - 즉시 폴백] lat={} lng={}", lat, lng);
        } catch (Exception e) {
            log.warn("카카오 역지오코딩 실패 lat={} lng={}: {}", lat, lng, e.getMessage());
        }
        return null;
    }
}
