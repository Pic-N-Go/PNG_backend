package com.project.picngo.external.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.picngo.external.AirQualityClient;
import com.project.picngo.external.WeatherClient;
import com.project.picngo.external.dto.AirQualityResponse;
import com.project.picngo.external.dto.GoldenHourResponse;
import com.project.picngo.external.dto.WeatherForecastResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeatherCacheService {

    private final WeatherForecastService weatherForecastService;
    private final WeatherClient weatherClient;
    private final AirQualityClient airQualityClient;

    // Redis 연동
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 3시간 TTL
    private static final long TTL_HOURS = 3;

    public List<WeatherForecastResponse> getCached7DayForecast(Double lat, Double lng, String date) {
        String key = "weather:forecast:7days:" + String.format(Locale.US, "%.1f_%.1f", lat, lng);
        
        String cachedData = redisTemplate.opsForValue().get(key);
        if (cachedData != null) {
            try {
                log.info("캐시 히트(Cache Hit) 발생: 단기예보 - {}", key);
                return objectMapper.readValue(cachedData, new com.fasterxml.jackson.core.type.TypeReference<List<WeatherForecastResponse>>() {});
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                log.warn("Redis 단기예보 파싱 실패 (key: {})", key, e);
            }
        }

        List<WeatherForecastResponse> freshData = weatherForecastService.getCombined7DayForecast(lat, lng, date);
        
        if (freshData != null && !freshData.isEmpty()) {
            try {
                redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(freshData), TTL_HOURS, java.util.concurrent.TimeUnit.HOURS);
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                log.warn("Redis 단기예보 저장 실패 (key: {})", key, e);
            }
        }
        
        return freshData;
    }

    public GoldenHourResponse getCachedGoldenHour(Double lat, Double lng, String targetDate) {
        String key = "weather:goldenhour:" + String.format(Locale.US, "%.0f_%.0f_%s", lat, lng, targetDate);
        
        String cachedData = redisTemplate.opsForValue().get(key);
        if (cachedData != null) {
            try {
                log.info("캐시 히트(Cache Hit) 발생: 골든아워 - {}", key);
                return objectMapper.readValue(cachedData, GoldenHourResponse.class);
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                log.warn("Redis 골든아워 파싱 실패 (key: {})", key, e);
            }
        }

        GoldenHourResponse freshData = weatherClient.getGoldenHour(lat, lng, targetDate);
        
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(freshData), TTL_HOURS, java.util.concurrent.TimeUnit.HOURS);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.warn("Redis 골든아워 저장 실패 (key: {})", key, e);
        }
        
        return freshData;
    }

    public AirQualityResponse.Item getCachedAirQuality(String sidoName) {
        String key = "weather:airquality:" + sidoName;
        
        String cachedData = redisTemplate.opsForValue().get(key);
        if (cachedData != null) {
            try {
                log.info("캐시 히트(Cache Hit) 발생: 미세먼지 - {}", key);
                return objectMapper.readValue(cachedData, AirQualityResponse.Item.class);
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                log.warn("Redis 미세먼지 파싱 실패 (key: {})", key, e);
            }
        }

        AirQualityResponse.Item freshData = airQualityClient.getAirQuality(sidoName);
        if (freshData != null) {
            try {
                redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(freshData), TTL_HOURS, java.util.concurrent.TimeUnit.HOURS);
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                log.warn("Redis 미세먼지 저장 실패 (key: {})", key, e);
            }
        }
        return freshData;
    }
}
