package com.project.picngo.external;

import com.project.picngo.common.util.LatXLngYConverter;
import com.project.picngo.external.dto.GoldenHourResponse;
import com.project.picngo.external.dto.KmaWeatherApiResponse;
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

    private final WebClient kmaWebClient;
    private final WebClient sunriseWebClient;
    private final String serviceKey;

    public WeatherClient(
            WebClient.Builder webClientBuilder, 
            @Value("${weather.api.key}") String serviceKey,
            @Value("${weather.api.kma-url:http://apis.data.go.kr/1360000/VilageFcstInfoService_2.0}") String kmaUrl,
            @Value("${weather.api.sunrise-url:https://api.sunrise-sunset.org}") String sunriseUrl) {
        this.kmaWebClient = webClientBuilder.clone().baseUrl(kmaUrl).build();
        this.sunriseWebClient = webClientBuilder.clone().baseUrl(sunriseUrl).build();
        this.serviceKey = serviceKey;
    }

    public List<WeatherForecastResponse> getForecast(Double lat, Double lng, String date) {
        LatXLngYConverter.LatXLngY grid = LatXLngYConverter.convertGrid(lat, lng);

        KmaWeatherApiResponse apiResponse;
        try {
            apiResponse = kmaWebClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/getVilageFcst")
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
            log.error("기상청 API 호출 실패", e);
            throw new CustomException(ExternalApiErrorCode.WEATHER_API_ERROR);
        }

        List<WeatherForecastResponse> result = new ArrayList<>();
        if (apiResponse != null && apiResponse.response() != null && apiResponse.response().body() != null && apiResponse.response().body().items() != null) {
            Map<String, Map<String, String>> groupedData = new HashMap<>();
            
            for (KmaWeatherApiResponse.Item item : apiResponse.response().body().items().item()) {
                String key = item.fcstDate() + item.fcstTime();
                groupedData.putIfAbsent(key, new HashMap<>());
                groupedData.get(key).put(item.category(), item.fcstValue());
            }

            for (Map.Entry<String, Map<String, String>> entry : groupedData.entrySet()) {
                String key = entry.getKey();
                String fcstDate = key.substring(0, 8);
                String fcstTime = key.substring(8);
                Map<String, String> values = entry.getValue();

                String pty = values.getOrDefault("PTY", "0");
                String sky = values.getOrDefault("SKY", "1");
                String tmpStr = values.getOrDefault("TMP", "0");
                
                String weatherStatus = "CLEAR";
                if ("1".equals(pty) || "4".equals(pty)) weatherStatus = "RAINY";
                else if ("2".equals(pty) || "3".equals(pty)) weatherStatus = "SNOWY";
                else if ("3".equals(sky) || "4".equals(sky)) weatherStatus = "CLOUDY";

                result.add(new WeatherForecastResponse(fcstDate, fcstTime, weatherStatus, Double.parseDouble(tmpStr)));
            }
        }
        return result;
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
