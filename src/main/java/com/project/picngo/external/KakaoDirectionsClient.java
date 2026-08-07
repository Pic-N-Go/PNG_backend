package com.project.picngo.external;

import com.project.picngo.external.dto.DirectionsResponse;
import com.project.picngo.external.dto.KakaoDirectionsApiResponse;
import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.ExternalApiErrorCode;
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
public class KakaoDirectionsClient implements DirectionsClient {

    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(3);

    private final WebClient webClient;
    private final String apiKey;
    private final CircuitBreaker circuitBreaker;

    public KakaoDirectionsClient(
            WebClient.Builder webClientBuilder,
            CircuitBreakerRegistry circuitBreakerRegistry,
            @Value("${kakao.rest.api.key}") String apiKey,
            @Value("${kakao.directions.base-url}") String baseUrl) {

        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
        // 코스 이동시간은 스팟 쌍마다 이 API를 부르므로, 카카오가 느려지면 코스 하나 저장에
        // 스팟 수만큼 대기가 누적된다. 빠르게 실패시켜 톰캣 스레드 점유를 끊는다.
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("kakaoDirections");
    }

    public DirectionsResponse getTravelInfo(Double startLat, Double startLng, Double goalLat, Double goalLng) {
        // 서킷브레이커로 감싸는 범위는 HTTP 호출까지다. result_code 102/103(비도로 목적지)은
        // 카카오가 정상 응답한 "그 좌표에 길이 없다"는 결과지 API 장애가 아니므로,
        // 산속 스팟이 많은 코스 하나 때문에 서킷이 열리면 안 된다.
        Supplier<KakaoDirectionsApiResponse> call = CircuitBreaker.decorateSupplier(circuitBreaker, () ->
                webClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .queryParam("origin", startLng + "," + startLat)
                                .queryParam("destination", goalLng + "," + goalLat)
                                .build())
                        .header("Authorization", "KakaoAK " + apiKey)
                        .retrieve()
                        .bodyToMono(KakaoDirectionsApiResponse.class)
                        .timeout(CALL_TIMEOUT)
                        .block());

        KakaoDirectionsApiResponse apiResponse;
        try {
            apiResponse = call.get();
        } catch (CallNotPermittedException e) {
            // 서킷 open - 호출을 시도조차 하지 않고 즉시 실패. 기존 계약(예외)을 그대로 유지해
            // RouteCacheService가 폴백 추정 계산으로 넘어가게 한다.
            log.warn("⚡ [카카오 길찾기 서킷 open - 즉시 실패] 출발지: ({},{}), 목적지: ({},{})",
                    startLat, startLng, goalLat, goalLng);
            throw new CustomException(ExternalApiErrorCode.KAKAO_API_ERROR);
        } catch (Exception e) {
            log.warn("카카오 길찾기 API 호출 실패: {}", e.getMessage());
            throw new CustomException(ExternalApiErrorCode.KAKAO_API_ERROR);
        }

        log.info("🚗 [카카오 길찾기 API 요청] 출발지: ({},{}), 목적지: ({},{})", startLat, startLng, goalLat, goalLng);

        if (apiResponse != null && apiResponse.routes() != null && !apiResponse.routes().isEmpty()) {
            KakaoDirectionsApiResponse.Route route = apiResponse.routes().get(0);
            log.info("🚗 [카카오 길찾기 API 응답] result_code: {}, result_msg: {}, summary: {}",
                    route.result_code(), route.result_msg(), route.summary());

            // result_code는 Integer라 응답에 필드가 없으면 null이다.
            // null 검사 없이 == 0으로 비교하면 언박싱 NPE가 나고,
            // 호출부에서는 파싱 실패가 API 호출 실패로 둔갑해 실패 사유를 잃는다.
            Integer resultCode = route.result_code();

            if (resultCode != null && resultCode == 0) {
                KakaoDirectionsApiResponse.Summary summary = route.summary();
                int minutes = summary.duration() / 60; // 카카오는 초 단위 반환
                return new DirectionsResponse(minutes, summary.distance(), 0);
            } else {
                log.warn("⚠️ 카카오 길찾기 경로 탐색 실패 (API 응답코드: {}, 메세지: {})", resultCode, route.result_msg());
                return new DirectionsResponse(null, null, resultCode);
            }
        }
        log.warn("❌ 카카오 길찾기 API 응답값 없음");
        return null;
    }

    public Integer getTravelTimeMinutes(Double startLat, Double startLng, Double goalLat, Double goalLng) {
        DirectionsResponse response = getTravelInfo(startLat, startLng, goalLat, goalLng);
        return response != null ? response.travelTimeMinutes() : null;
    }
}
