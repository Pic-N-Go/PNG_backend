package com.project.picngo.external;

import com.project.picngo.common.util.LatXLngYConverter;
import com.project.picngo.external.dto.GoldenHourResponse;
import com.project.picngo.external.dto.KmaWeatherApiResponse;
import com.project.picngo.external.dto.KmaMidWeatherApiResponse;
import com.project.picngo.external.dto.SunriseSunsetApiResponse;
import com.project.picngo.external.dto.WeatherForecastResponse;
import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.ExternalApiErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class WeatherClient {

    private final String serviceKey;
    private final WebClient kmaWebClient;
    private final WebClient sunriseWebClient;

    public WeatherClient(WebClient.Builder webClientBuilder,
                         @Value("${weather.api.kma-url:http://apis.data.go.kr/1360000}") String kmaUrl,
                         @Value("${weather.api.sunrise-url:https://api.sunrise-sunset.org}") String sunriseUrl,
                         @Value("${weather.api.key}") String serviceKey) {
        this.serviceKey = serviceKey;
        
        ExchangeStrategies exchangeStrategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();

        this.kmaWebClient = webClientBuilder.clone()
                .baseUrl(kmaUrl)
                .exchangeStrategies(exchangeStrategies)
                .defaultHeader("User-Agent", "Mozilla/5.0")
                .build();
                
        this.sunriseWebClient = webClientBuilder.clone()
                .baseUrl(sunriseUrl)
                .build();
    }

    private String[] getLatestBaseDateAndTime() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        // 무조건 어제 2300 발표 데이터 사용
        return new String[]{
                now.minusDays(1).format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")),
                "2300"
        };
    }

    public List<WeatherForecastResponse> getShortTermForecast(Double lat, Double lng, String date) {
        LatXLngYConverter.LatXLngY grid = LatXLngYConverter.convertGrid(lat, lng);

        KmaWeatherApiResponse apiResponse;
        try {
            String[] baseDateTime = getLatestBaseDateAndTime();
            String baseDate = baseDateTime[0];
            String baseTime = baseDateTime[1];

            String urlStr = "http://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getVilageFcst"
                    + "?serviceKey=" + serviceKey
                    + "&pageNo=1"
                    + "&numOfRows=1000"
                    + "&dataType=JSON"
                    + "&base_date=" + baseDate
                    + "&base_time=" + baseTime
                    + "&nx=" + grid.x
                    + "&ny=" + grid.y;
                    
            log.debug("Requesting KMA API with Dynamic BaseTime: {} {}, grid: {},{}", baseDate, baseTime, grid.x, grid.y);
            java.net.URI uri = new java.net.URI(urlStr);

            apiResponse = kmaWebClient.get()
                    .uri(uri)
                    .retrieve()
                    .bodyToMono(KmaWeatherApiResponse.class)
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(2))
                            .doBeforeRetry(retrySignal -> log.warn("기상청 단기예보 502/타임아웃 발생, 재시도합니다... ({}회차)", retrySignal.totalRetries() + 1)))
                    .block();

            if (apiResponse == null) {
                log.error("기상청 API 응답이 null입니다.");
                throw new CustomException(ExternalApiErrorCode.WEATHER_API_ERROR);
            }
        } catch (Exception e) {
            log.warn("기상청 단기예보 API 호출 실패: {}", e.getMessage());
            throw new CustomException(ExternalApiErrorCode.WEATHER_API_ERROR);
        }

        List<WeatherForecastResponse> result = new ArrayList<>();
        if (apiResponse.response() == null || apiResponse.response().body() == null 
                || apiResponse.response().body().items() == null || apiResponse.response().body().items().item() == null) {
            String errMsg = "API Header Code: " + (apiResponse.response() != null && apiResponse.response().header() != null ? apiResponse.response().header().resultCode() : "NULL");
            log.error("기상청 응답에 body가 없습니다. {}", errMsg);
            throw new CustomException(ExternalApiErrorCode.WEATHER_API_ERROR);
        }

        record ForecastKey(String date, String time) {}
        Map<ForecastKey, Map<String, String>> groupedData = new HashMap<>();
        
        for (KmaWeatherApiResponse.Item item : apiResponse.response().body().items().item()) {
            ForecastKey key = new ForecastKey(item.fcstDate(), item.fcstTime());
            groupedData.putIfAbsent(key, new HashMap<>());
            groupedData.get(key).put(item.category(), item.fcstValue());
        }

        for (Map.Entry<ForecastKey, Map<String, String>> entry : groupedData.entrySet()) {
            ForecastKey key = entry.getKey();
            Map<String, String> values = entry.getValue();

            String pty = values.getOrDefault("PTY", "0");
            String sky = values.getOrDefault("SKY", "1");
            String tmpStr = values.getOrDefault("TMP", "0");
            
            String weatherStatus = "CLEAR";
            if ("1".equals(pty) || "4".equals(pty)) weatherStatus = "RAINY";
            else if ("2".equals(pty) || "3".equals(pty)) weatherStatus = "SNOWY";
            else if ("3".equals(sky) || "4".equals(sky)) weatherStatus = "CLOUDY";

            double temperature = 0.0;
            try {
                temperature = Double.parseDouble(tmpStr);
            } catch (NumberFormatException e) {
                log.warn("온도 파싱 실패 (날짜: {}, 시간: {}, 값: {}) - 기본값 0.0 적용", key.date(), key.time(), tmpStr);
            }

            result.add(new WeatherForecastResponse(key.date(), key.time(), weatherStatus, temperature));
        }
        return result;
    }

    public KmaMidWeatherApiResponse getMidTermForecast(String regId, String tmFc) {
        try {
            String urlStr = "https://apis.data.go.kr/1360000/MidFcstInfoService/getMidLandFcst"
                    + "?serviceKey=" + serviceKey
                    + "&pageNo=1"
                    + "&numOfRows=10"
                    + "&dataType=JSON"
                    + "&regId=" + regId
                    + "&tmFc=" + tmFc;
            log.debug("[KMA MidTerm API Request] URL: {}", urlStr);
            java.net.URI uri = new java.net.URI(urlStr);

            return kmaWebClient.get()
                    .uri(uri)
                    .retrieve()
                    .bodyToMono(KmaMidWeatherApiResponse.class)
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(2))
                            .doBeforeRetry(retrySignal -> log.warn("기상청 중기예보 에러 발생, 재시도합니다... ({}회차)", retrySignal.totalRetries() + 1)))
                    .block();
        } catch (Exception e) {
            log.warn("기상청 중기예보 에러 발생: {}", e.getMessage());
            throw new CustomException(ExternalApiErrorCode.WEATHER_API_ERROR);
        }
    }

    public GoldenHourResponse getGoldenHour(Double lat, Double lng, String date) {
        SunriseSunsetApiResponse apiResponse;
        try {
            apiResponse = sunriseWebClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/json")
                            .queryParam("lat", lat)
                            .queryParam("lng", lng)
                            .queryParam("date", date)
                            .queryParam("formatted", 0)
                            .build())
                    .retrieve()
                    .bodyToMono(SunriseSunsetApiResponse.class)
                    .retryWhen(Retry.backoff(2, Duration.ofSeconds(1)))
                    .block();
        } catch (Exception e) {
            log.error("Sunrise API 호출 실패", e);
            throw new CustomException(ExternalApiErrorCode.WEATHER_API_ERROR);
        }

        if (apiResponse != null && "OK".equals(apiResponse.status())) {
            String sunriseUtc = apiResponse.results().sunrise();
            String sunsetUtc = apiResponse.results().sunset();
            return new GoldenHourResponse(sunriseUtc, sunsetUtc, "Morning Golden Hour", "Evening Golden Hour");
        }
        throw new CustomException(ExternalApiErrorCode.WEATHER_API_ERROR);
    }
}
