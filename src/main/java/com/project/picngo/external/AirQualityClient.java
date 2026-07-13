package com.project.picngo.external;

import com.project.picngo.external.dto.AirQualityResponse.Item;
import com.project.picngo.external.dto.AirQualityResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Component
public class AirQualityClient {

    // 대기오염정보 Open API
    private static final String BASE_URL = "https://apis.data.go.kr/B552584/ArpltnInforInqireSvc";

    private final WebClient webClient;
    private final String serviceKey;

    public AirQualityClient(WebClient.Builder builder, @Value("${air.quality.key}") String serviceKey) {
        this.webClient = builder.baseUrl(BASE_URL).build();
        this.serviceKey = serviceKey;
    }

    public Item getAirQuality(String sidoName) {
        try {
            AirQualityResponse response = webClient.get()
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
                    .block();

            if (response != null && response.response() != null
                    && response.response().body() != null
                    && response.response().body().items() != null) {
                return response.response().body().items().stream()
                        .filter(i -> i.pm10Value() != null && !i.pm10Value().equals("-"))
                        .findFirst()
                        .orElse(null);
            }
        } catch (Exception e) {
            log.error("에어코리아 API 호출 실패 sidoName={}", sidoName, e);
        }
        return null;
    }
}
