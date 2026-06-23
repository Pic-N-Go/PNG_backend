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
import java.util.List;

@Slf4j
@Component
public class WeatherClient {

    private final WebClient kmaWebClient;
    private final WebClient sunriseWebClient;
    private final String serviceKey;

    public WeatherClient(WebClient.Builder webClientBuilder, @Value("${weather.api.key}") String serviceKey) {
        this.kmaWebClient = webClientBuilder.clone().baseUrl("http://apis.data.go.kr/1360000/VilageFcstInfoService_2.0").build();
        this.sunriseWebClient = webClientBuilder.clone().baseUrl("https://api.sunrise-sunset.org").build();
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
        if (apiResponse != null && apiResponse.response().body() != null) {
            result.add(new WeatherForecastResponse(date, "1200", "CLEAR", 22.5));
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
