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
