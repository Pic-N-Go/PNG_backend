package com.project.picngo.external;

import com.project.picngo.external.dto.AirQualityResponse.Item;
import com.project.picngo.external.dto.AirQualityResponse;
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
public class AirQualityClient {

    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(3);

    private final WebClient webClient;
    private final String serviceKey;
    private final CircuitBreaker circuitBreaker;

    public AirQualityClient(WebClient.Builder builder,
                            CircuitBreakerRegistry circuitBreakerRegistry,
                            @Value("${air.quality.key}") String serviceKey,
                            @Value("${air.quality.base-url}") String baseUrl) {
        this.webClient = builder.baseUrl(baseUrl).build();
        this.serviceKey = serviceKey;
        // 기상청과 같은 apis.data.go.kr지만 서킷은 분리한다. 이 호스트는 공공데이터포털이라는
        // 게이트웨이일 뿐이고 뒤의 실제 서비스는 기관별로 따로 돌아간다(여기는 한국환경공단).
        // 에어코리아가 죽어도 기상청 예보는 계속 나가야 한다.
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("airQuality");
    }

    public Item getAirQuality(String sidoName) {
        try {
            Supplier<AirQualityResponse> call = CircuitBreaker.decorateSupplier(circuitBreaker, () ->
                    webClient.get()
                            .uri(uriBuilder -> uriBuilder.path("/getCtprvnRltmMesureDnsty")
                                    .queryParam("serviceKey", serviceKey)
                                    .queryParam("sidoName", sidoName)
                                    .queryParam("pageNo", 1)
                                    .queryParam("numOfRows", 40)
                                    .queryParam("returnType", "json")
                                    .queryParam("ver", "1.0")
                                    .build())
                            .retrieve()
                            .bodyToMono(AirQualityResponse.class)
                            .timeout(CALL_TIMEOUT)
                            .block());

            AirQualityResponse response = call.get();

            if (response != null && response.response() != null
                    && response.response().body() != null
                    && response.response().body().items() != null) {
                return response.response().body().items().stream()
                        .filter(i -> i.pm10Value() != null && !i.pm10Value().equals("-"))
                        .findFirst()
                        .orElse(null);
            }
        } catch (CallNotPermittedException e) {
            // 원래도 실패 시 null을 돌려주던 구조라 서킷 open도 같은 폴백을 탄다.
            log.warn("⚡ [에어코리아 서킷 open - 즉시 폴백] sidoName={}", sidoName);
        } catch (Exception e) {
            // 스택트레이스는 남기지 않는다. 외부 API 실패는 예상된 상황이고 폴백으로 처리되며,
            // 장애 시 초당 수백 건이면 스택이 로그를 가득 채운다.
            log.warn("에어코리아 API 호출 실패 sidoName={}: {}", sidoName, e.getMessage());
        }
        return null;
    }
}
