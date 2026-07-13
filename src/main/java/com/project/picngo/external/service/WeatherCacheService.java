package com.project.picngo.external.service;

import com.project.picngo.external.AirQualityClient;
import com.project.picngo.external.WeatherClient;
import com.project.picngo.external.dto.AirQualityResponse;
import com.project.picngo.external.dto.GoldenHourResponse;
import com.project.picngo.external.dto.WeatherForecastResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeatherCacheService {

    private final WeatherForecastService weatherForecastService;
    private final WeatherClient weatherClient;
    private final AirQualityClient airQualityClient;

    // 인메모리 캐시 (간이 구현)
    // Key: lat_lng, Value: CacheEntry
    private final Map<String, CacheEntry<List<WeatherForecastResponse>>> forecastCache = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<GoldenHourResponse>> goldenHourCache = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<AirQualityResponse.Item>> airQualityCache = new ConcurrentHashMap<>();

    // 3시간 TTL
    private static final long TTL_MILLIS = 3 * 60 * 60 * 1000;

    public List<WeatherForecastResponse> getCached7DayForecast(Double lat, Double lng, String date) {
        String key = String.format("%.3f_%.3f", lat, lng); // 소수점 3자리(약 100m 오차)로 클러스터링
        
        CacheEntry<List<WeatherForecastResponse>> entry = forecastCache.get(key);
        if (entry != null && !entry.isExpired()) {
            return entry.getData();
        }

        List<WeatherForecastResponse> freshData = weatherForecastService.getCombined7DayForecast(lat, lng, date);
        forecastCache.put(key, new CacheEntry<>(freshData, System.currentTimeMillis()));
        return freshData;
    }

    public GoldenHourResponse getCachedGoldenHour(Double lat, Double lng, String targetDate) {
        String key = String.format("%.3f_%.3f_%s", lat, lng, targetDate);
        
        CacheEntry<GoldenHourResponse> entry = goldenHourCache.get(key);
        if (entry != null && !entry.isExpired()) {
            return entry.getData();
        }

        GoldenHourResponse freshData = weatherClient.getGoldenHour(lat, lng, targetDate);
        goldenHourCache.put(key, new CacheEntry<>(freshData, System.currentTimeMillis()));
        return freshData;
    }

    public AirQualityResponse.Item getCachedAirQuality(String sidoName) {
        CacheEntry<AirQualityResponse.Item> entry = airQualityCache.get(sidoName);
        if (entry != null && !entry.isExpired()) {
            return entry.getData();
        }

        AirQualityResponse.Item freshData = airQualityClient.getAirQuality(sidoName);
        if (freshData != null) {
            airQualityCache.put(sidoName, new CacheEntry<>(freshData, System.currentTimeMillis()));
        }
        return freshData;
    }

    private static class CacheEntry<T> {
        private final T data;
        private final long timestamp;

        public CacheEntry(T data, long timestamp) {
            this.data = data;
            this.timestamp = timestamp;
        }

        public T getData() {
            return data;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - timestamp > TTL_MILLIS;
        }
    }
}
