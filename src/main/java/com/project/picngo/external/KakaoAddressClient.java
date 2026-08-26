package com.project.picngo.external;

import com.project.picngo.external.dto.KakaoAddressApiResponse;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.function.Supplier;

@Slf4j
@Component
public class KakaoAddressClient {

    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(3);
    private final WebClient webClient;
    private final String apiKey;
    private final CircuitBreaker circuitBreaker;

    public KakaoAddressClient(
            WebClient.Builder webClientBuilder,
            CircuitBreakerRegistry circuitBreakerRegistry,
            @Value("${kakao.rest.api.key}") String apiKey,
            @Value("${kakao.local.base-url:https://dapi.kakao.com}") String baseUrl) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("kakaoAddress");
    }

    public String coord2Address(Double latitude, Double longitude) {
        if (!isValidCoordinate(latitude, longitude)) {
            return null;
        }

        try {
            Supplier<KakaoAddressApiResponse> call = CircuitBreaker.decorateSupplier(
                    circuitBreaker, () -> requestAddress(latitude, longitude));
            KakaoAddressApiResponse response = call.get();

            return resolveAddress(response);
        } catch (CallNotPermittedException e) {
            log.warn("카카오 주소 변환 서킷브레이커가 열려 있습니다. lat={} lng={}", latitude, longitude);
            return null;
        } catch (Exception e) {
            log.warn("카카오 주소 변환에 실패했습니다. lat={} lng={}: {}", latitude, longitude, e.getMessage());
            return null;
        }
    }

    private KakaoAddressApiResponse requestAddress(Double latitude, Double longitude) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v2/local/geo/coord2address.json")
                        .queryParam("x", longitude)
                        .queryParam("y", latitude)
                        .queryParam("input_coord", "WGS84")
                        .build())
                .header("Authorization", "KakaoAK " + apiKey)
                .retrieve()
                .bodyToMono(KakaoAddressApiResponse.class)
                .timeout(CALL_TIMEOUT)
                .block();
    }

    private String resolveAddress(KakaoAddressApiResponse response) {
        if (response == null || response.documents() == null || response.documents().isEmpty()) {
            return null;
        }

        KakaoAddressApiResponse.Document document = response.documents().get(0);

        String roadAddress = getAddressName(document.roadAddress());

        if (StringUtils.hasText(roadAddress)) {
            return roadAddress;
        }

        String lotNumberAddress = getAddressName(document.address());

        if (StringUtils.hasText(lotNumberAddress)) {
            return lotNumberAddress;
        }

        return null;
    }

    private String getAddressName(KakaoAddressApiResponse.Address address) {
        return address == null ? null : address.addressName();
    }

    private boolean isValidCoordinate(Double latitude, Double longitude) {
        return latitude != null
                && longitude != null
                && Double.isFinite(latitude)
                && Double.isFinite(longitude)
                && latitude >= -90.0
                && latitude <= 90.0
                && longitude >= -180.0
                && longitude <= 180.0;
    }

}
