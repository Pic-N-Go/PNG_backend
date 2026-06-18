package com.project.picngo.external;

import com.project.picngo.external.dto.DirectionsResponse;
import com.project.picngo.external.dto.KakaoDirectionsApiResponse;
import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.ExternalApiErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Component
public class KakaoDirectionsClient implements DirectionsClient {

    private final WebClient webClient;
    private final String apiKey;

    public KakaoDirectionsClient(
            WebClient.Builder webClientBuilder,
            @Value("${kakao.rest.api.key}") String apiKey) {
        
        this.webClient = webClientBuilder.baseUrl("https://apis-navi.kakaomobility.com/v1/directions").build();
        this.apiKey = apiKey;
        log.info("[DEBUG] KAKAO_REST_API_KEY 로딩됨: {}", apiKey != null && !apiKey.isEmpty() ? "로딩 성공 (길이: " + apiKey.length() + ")" : "NULL");
    }

    public DirectionsResponse getTravelInfo(Double startLat, Double startLng, Double goalLat, Double goalLng) {
        KakaoDirectionsApiResponse apiResponse;
        try {
            apiResponse = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("origin", startLng + "," + startLat)
                            .queryParam("destination", goalLng + "," + goalLat)
                            .build())
                    .header("Authorization", "KakaoAK " + apiKey)
                    .retrieve()
                    .bodyToMono(KakaoDirectionsApiResponse.class)
                    .block();
        } catch (Exception e) {
            log.error("카카오 길찾기 API 호출 실패", e);
            throw new CustomException(ExternalApiErrorCode.KAKAO_API_ERROR);
        }

        if (apiResponse != null && apiResponse.routes() != null && !apiResponse.routes().isEmpty()) {
            KakaoDirectionsApiResponse.Route route = apiResponse.routes().get(0);
            if (route.result_code() == 0) {
                KakaoDirectionsApiResponse.Summary summary = route.summary();
                int minutes = summary.duration() / 60; // 카카오는 초 단위 반환
                return new DirectionsResponse(minutes, summary.distance());
            } else {
                log.error("카카오 길찾기 API 비정상 응답: {}", route.result_msg());
            }
        }
        throw new CustomException(ExternalApiErrorCode.KAKAO_API_ERROR);
    }

    public Integer getTravelTimeMinutes(Double startLat, Double startLng, Double goalLat, Double goalLng) {
        return getTravelInfo(startLat, startLng, goalLat, goalLng).travelTimeMinutes();
    }
}
