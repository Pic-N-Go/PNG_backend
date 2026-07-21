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
            log.warn("단기예보 병합 실패: {}", e.getMessage());
        }

        try {
            // 기준 발표 시각을 '무조건 어제 18시'로 고정하여 기상청 지연 업데이트 예방
            java.time.LocalDate todayDate = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
            String tmFc = todayDate.minusDays(1).format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")) + "1800";
            
            // nx, ny (lat, lng)를 광역구역코드(regId)로 변환
            String regId = getMidTermRegionCode(lat, lng); 
            
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
            log.warn("중기예보 병합 실패: {}", e.getMessage());
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

    /**
     * 위도/경도를 기반으로 기상청 중기예보 육상구역코드(regId)를 대략적으로 매핑합니다.
     * 기상 예보는 광역 단위로 이루어지므로 Bounding Box를 이용한 근사치를 사용합니다.
     */
    private String getMidTermRegionCode(Double lat, Double lng) {
        if (lat < 34.0) {
            return "11G00000"; // 제주도
        }
        if (lat < 36.0) {
            if (lng < 127.5) return "11F20000"; // 광주, 전라남도, 전라북도
            else return "11H20000"; // 부산, 울산, 경상남도, 경상북도
        }
        if (lat < 37.0) {
            return "11C20000"; // 대전, 세종, 충청남도, 충청북도
        }
        if (lng > 127.8) {
            return "11D10000"; // 강원도 (영서/영동 통합)
        }
        return "11B00000"; // 서울, 인천, 경기도 (기본값)
    }
}
