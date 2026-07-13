package com.project.picngo.external.service;

import com.project.picngo.external.WeatherClient;
import com.project.picngo.external.dto.KmaMidWeatherApiResponse;
import com.project.picngo.external.dto.WeatherForecastResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeatherForecastService {

    private final WeatherClient weatherClient;

    /**
     * 단기예보(1~3일)와 중기예보(3~7일)를 병합하여 반환합니다.
     */
    public List<WeatherForecastResponse> getCombined7DayForecast(Double lat, Double lng, String date) {
        List<WeatherForecastResponse> combined = new ArrayList<>();
        
        try {
            // 1. 단기예보 (시간별)
            List<WeatherForecastResponse> shortTerm = weatherClient.getShortTermForecast(lat, lng, date);
            combined.addAll(shortTerm);
        } catch (Exception e) {
            log.error("단기예보 병합 실패", e);
        }

        try {
            // 2. 중기예보 (오전/오후)
            // 기준 발표 시각을 구합니다. (오늘 오전 6시 기준)
            String tmFc = LocalDate.now(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "0600";
            
            // TODO: 추후 nx, ny (lat, lng)를 광역구역코드(regId)로 변환하는 매퍼 추가 필요
            // 임시로 서울/경기(11B00000) 고정
            String regId = "11B00000"; 
            
            KmaMidWeatherApiResponse midTermResponse = weatherClient.getMidTermForecast(regId, tmFc);
            
            if (midTermResponse != null && midTermResponse.response() != null 
                    && midTermResponse.response().body() != null
                    && midTermResponse.response().body().items() != null
                    && !midTermResponse.response().body().items().item().isEmpty()) {
                
                KmaMidWeatherApiResponse.Item item = midTermResponse.response().body().items().item().get(0);
                LocalDate baseDate = LocalDate.now(ZoneId.of("Asia/Seoul"));

                // 중기 예보는 Day 3 ~ Day 7
                combined.addAll(extractMidTerm(item, baseDate, 3, item.wf3Am(), item.wf3Pm()));
                combined.addAll(extractMidTerm(item, baseDate, 4, item.wf4Am(), item.wf4Pm()));
                combined.addAll(extractMidTerm(item, baseDate, 5, item.wf5Am(), item.wf5Pm()));
                combined.addAll(extractMidTerm(item, baseDate, 6, item.wf6Am(), item.wf6Pm()));
                combined.addAll(extractMidTerm(item, baseDate, 7, item.wf7Am(), item.wf7Pm()));
            }

        } catch (Exception e) {
            log.error("중기예보 병합 실패", e);
        }

        return combined;
    }

    private List<WeatherForecastResponse> extractMidTerm(KmaMidWeatherApiResponse.Item item, LocalDate baseDate, int plusDays, String wfAm, String wfPm) {
        List<WeatherForecastResponse> list = new ArrayList<>();
        String targetDate = baseDate.plusDays(plusDays).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        
        // 오전(AM)은 1000으로 매핑
        if (wfAm != null) {
            list.add(new WeatherForecastResponse(targetDate, "1000", mapWfToStatus(wfAm), 0.0));
        }
        // 오후(PM)는 1400, 1800으로 매핑
        if (wfPm != null) {
            list.add(new WeatherForecastResponse(targetDate, "1400", mapWfToStatus(wfPm), 0.0));
            list.add(new WeatherForecastResponse(targetDate, "1800", mapWfToStatus(wfPm), 0.0));
        }
        
        return list;
    }

    private String mapWfToStatus(String wf) {
        if (wf == null) return "CLEAR";
        if (wf.contains("비") || wf.contains("소나기")) return "RAINY";
        if (wf.contains("눈")) return "SNOWY";
        if (wf.contains("흐림") || wf.contains("구름많음")) return "CLOUDY";
        return "CLEAR";
    }
}
