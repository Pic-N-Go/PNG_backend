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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class WeatherClient {

    // 기상청 Open API 서비스키
    private final String serviceKey;

    // 기상청 날씨(단,중기)예보 Open API
    private final WebClient kmaWebClient;

    // Sunrise-Sunset API
    private final WebClient sunriseWebClient;

    public WeatherClient(
            WebClient.Builder webClientBuilder, 
            @Value("${weather.api.key}") String serviceKey,
            @Value("${weather.api.kma-url:http://apis.data.go.kr/1360000}") String kmaUrl,
            @Value("${weather.api.sunrise-url:https://api.sunrise-sunset.org}") String sunriseUrl) {
        this.kmaWebClient = webClientBuilder.clone().baseUrl(kmaUrl).build();
        this.sunriseWebClient = webClientBuilder.clone().baseUrl(sunriseUrl).build();
        this.serviceKey = serviceKey;
    }

    public List<WeatherForecastResponse> getShortTermForecast(Double lat, Double lng, String date) {
        LatXLngYConverter.LatXLngY grid = LatXLngYConverter.convertGrid(lat, lng);

        KmaWeatherApiResponse apiResponse;
        try {
            apiResponse = kmaWebClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/VilageFcstInfoService_2.0/getVilageFcst")
                            .queryParam("serviceKey", serviceKey)
                            .queryParam("pageNo", 1)
                            .queryParam("numOfRows", 100)
                            .queryParam("dataType", "JSON")
                            .queryParam("base_date", java.time.LocalDate.now().minusDays(1).format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")))
                            .queryParam("base_time", "2300")
                            .queryParam("nx", grid.x)
                            .queryParam("ny", grid.y)
                            .build())
                    .retrieve()
                    .bodyToMono(KmaWeatherApiResponse.class)
                    .block();
        } catch (Exception e) {
            log.error("기상청 단기예보 API 호출 실패", e);
            throw new CustomException(ExternalApiErrorCode.WEATHER_API_ERROR);
        }

        List<WeatherForecastResponse> result = new ArrayList<>();
        if (apiResponse == null || apiResponse.response() == null || apiResponse.response().body() == null 
                || apiResponse.response().body().items() == null || apiResponse.response().body().items().item() == null) {
            return result;
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
            return kmaWebClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/MidFcstInfoService/getMidLandFcst")
                            .queryParam("serviceKey", serviceKey)
                            .queryParam("pageNo", 1)
                            .queryParam("numOfRows", 10)
                            .queryParam("dataType", "JSON")
                            .queryParam("regId", regId)
                            .queryParam("tmFc", tmFc)
                            .build())
                    .retrieve()
                    .bodyToMono(KmaMidWeatherApiResponse.class)
                    .block();
        } catch (Exception e) {
            log.error("기상청 중기예보 API 호출 실패", e);
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
