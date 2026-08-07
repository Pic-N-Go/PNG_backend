package com.project.picngo.external;

import com.project.picngo.common.util.LatXLngYConverter;
import com.project.picngo.external.dto.GoldenHourResponse;
import com.project.picngo.external.dto.KmaWeatherApiResponse;
import com.project.picngo.external.dto.KmaMidWeatherApiResponse;
import com.project.picngo.external.dto.SunriseSunsetApiResponse;
import com.project.picngo.external.dto.WeatherForecastResponse;
import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.ExternalApiErrorCode;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
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
import java.util.function.Supplier;

@Slf4j
@Component
public class WeatherClient {

    /** 재시도까지 포함한 총 소요 시간 상한. 호출부 스레드가 묶이는 시간이 이만큼으로 제한된다. */
    private static final Duration KMA_TIMEOUT = Duration.ofSeconds(3);
    /** 일출일몰 API는 재시도가 2회(백오프 1초)라 총 예산을 더 준다. */
    private static final Duration SUNRISE_TIMEOUT = Duration.ofSeconds(5);

    private final String serviceKey;
    private final String kmaBaseUrl;
    private final WebClient kmaWebClient;
    private final WebClient sunriseWebClient;

    // 이 클래스는 독립적으로 죽는 두 호스트를 호출하므로 서킷도 둘로 나눈다.
    // 하나로 합치면 일출일몰 API 장애가 기상청 예보까지 차단해, 서킷브레이커가
    // 막으려던 연쇄 장애를 서킷이 만들어내는 꼴이 된다.
    private final CircuitBreaker kmaCircuitBreaker;
    private final CircuitBreaker sunriseCircuitBreaker;

    public WeatherClient(WebClient.Builder webClientBuilder,
                         CircuitBreakerRegistry circuitBreakerRegistry,
                         @Value("${weather.api.kma-url:http://apis.data.go.kr/1360000}") String kmaUrl,
                         @Value("${weather.api.sunrise-url:https://api.sunrise-sunset.org}") String sunriseUrl,
                         @Value("${weather.api.key}") String serviceKey) {
        this.serviceKey = serviceKey;
        this.kmaBaseUrl = kmaUrl;

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

        // 단기예보와 중기예보는 같은 호스트(기상청)라 서킷을 공유한다.
        // 그래야 양쪽 실패가 한 윈도우에 모여 장애를 더 빨리 판단할 수 있다.
        this.kmaCircuitBreaker = circuitBreakerRegistry.circuitBreaker("kmaWeather");
        this.sunriseCircuitBreaker = circuitBreakerRegistry.circuitBreaker("sunriseSunset");
    }

    private String[] getLatestBaseDateAndTime() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Seoul"));
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

            String urlStr = kmaBaseUrl + "/VilageFcstInfoService_2.0/getVilageFcst"
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

            // timeout을 retryWhen 뒤에 붙여 재시도까지 포함한 총 소요를 제한한다.
            // 앞에 붙이면 시도당 제한이라 재시도가 겹치며 대기가 배로 늘어난다.
            Supplier<KmaWeatherApiResponse> call = CircuitBreaker.decorateSupplier(kmaCircuitBreaker, () ->
                    kmaWebClient.get()
                            .uri(uri)
                            .retrieve()
                            .bodyToMono(KmaWeatherApiResponse.class)
                            .retryWhen(Retry.backoff(1, Duration.ofMillis(500))
                                    .doBeforeRetry(retrySignal -> log.warn("기상청 단기예보 502/타임아웃 발생, 재시도합니다... ({}회차)", retrySignal.totalRetries() + 1)))
                            .timeout(KMA_TIMEOUT)
                            .block());

            apiResponse = call.get();

            if (apiResponse == null) {
                log.error("기상청 API 응답이 null입니다.");
                throw new CustomException(ExternalApiErrorCode.WEATHER_API_ERROR);
            }
        } catch (CallNotPermittedException e) {
            log.warn("⚡ [기상청 단기예보 서킷 open - 즉시 실패] grid: {},{}", grid.x, grid.y);
            throw new CustomException(ExternalApiErrorCode.WEATHER_API_ERROR);
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
            String urlStr = kmaBaseUrl + "/MidFcstInfoService/getMidLandFcst"
                    + "?serviceKey=" + serviceKey
                    + "&pageNo=1"
                    + "&numOfRows=10"
                    + "&dataType=JSON"
                    + "&regId=" + regId
                    + "&tmFc=" + tmFc;
            log.debug("[KMA MidTerm API Request] URL: {}", urlStr);
            java.net.URI uri = new java.net.URI(urlStr);

            // 단기예보와 같은 기상청 호스트라 서킷을 공유한다.
            Supplier<KmaMidWeatherApiResponse> call = CircuitBreaker.decorateSupplier(kmaCircuitBreaker, () ->
                    kmaWebClient.get()
                            .uri(uri)
                            .retrieve()
                            .bodyToMono(KmaMidWeatherApiResponse.class)
                            .retryWhen(Retry.backoff(1, Duration.ofMillis(500))
                                    .doBeforeRetry(retrySignal -> log.warn("기상청 중기예보 에러 발생, 재시도합니다... ({}회차)", retrySignal.totalRetries() + 1)))
                            .timeout(KMA_TIMEOUT)
                            .block());

            return call.get();
        } catch (CallNotPermittedException e) {
            log.warn("⚡ [기상청 중기예보 서킷 open - 즉시 실패] regId: {}", regId);
            throw new CustomException(ExternalApiErrorCode.WEATHER_API_ERROR);
        } catch (Exception e) {
            log.warn("기상청 중기예보 에러 발생: {}", e.getMessage());
            throw new CustomException(ExternalApiErrorCode.WEATHER_API_ERROR);
        }
    }

    public GoldenHourResponse getGoldenHour(Double lat, Double lng, String date) {
        SunriseSunsetApiResponse apiResponse;
        try {
            // 기상청과 다른 호스트라 서킷도 별개다. 이 API가 죽어도 날씨 예보는 계속 나가야 한다.
            Supplier<SunriseSunsetApiResponse> call = CircuitBreaker.decorateSupplier(sunriseCircuitBreaker, () ->
                    sunriseWebClient.get()
                            .uri(uriBuilder -> uriBuilder.path("/json")
                                    .queryParam("lat", lat)
                                    .queryParam("lng", lng)
                                    .queryParam("date", date)
                                    .queryParam("formatted", 0)
                                    .build())
                            .retrieve()
                            .bodyToMono(SunriseSunsetApiResponse.class)
                            .retryWhen(Retry.backoff(2, Duration.ofSeconds(1)))
                            .timeout(SUNRISE_TIMEOUT)
                            .block());

            apiResponse = call.get();
        } catch (CallNotPermittedException e) {
            log.warn("⚡ [일출일몰 서킷 open - 즉시 실패] lat: {}, lng: {}", lat, lng);
            throw new CustomException(ExternalApiErrorCode.WEATHER_API_ERROR);
        } catch (Exception e) {
            // 스택트레이스는 남기지 않는다. 외부 API 실패는 예상된 상황이고,
            // 장애 시 초당 수백 건이면 스택이 로그를 가득 채운다.
            log.warn("Sunrise API 호출 실패: {}", e.getMessage());
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
