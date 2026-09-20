package com.project.picngo.external.service;

import com.project.picngo.external.WeatherClient;
import com.project.picngo.external.dto.KmaMidWeatherApiResponse;
import com.project.picngo.external.dto.WeatherForecastResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeatherForecastService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final WeatherClient weatherClient;

    /**
     * 단기예보(1~3일)와 중기예보(3~7일)를 병합하여 반환합니다.
     */
    public List<WeatherForecastResponse> getCombined7DayForecast(Double lat, Double lng, String date) {
        List<WeatherForecastResponse> combined = new ArrayList<>();
        Set<String> shortTermDates = new HashSet<>();

        try {
            // 1. 단기예보 (시간별)
            List<WeatherForecastResponse> shortTerm = weatherClient.getShortTermForecast(lat, lng, date);
            if (shortTerm != null) {
                combined.addAll(shortTerm);
                for (WeatherForecastResponse st : shortTerm) {
                    shortTermDates.add(st.date());
                }
            }
        } catch (Exception e) {
            log.warn("단기예보 병합 실패: {}", e.getMessage());
        }

        try {
            // 2. 중기예보 (일별) - 최신 유효 발표시각(tmFc) 동적 계산
            LocalDateTime now = LocalDateTime.now(KST);
            String tmFc = calculateLatestMidTermTmFc(now);
            String regId = getMidTermRegionCode(lat, lng);

            KmaMidWeatherApiResponse midTermResponse = weatherClient.getMidTermForecast(regId, tmFc);

            if (midTermResponse != null && midTermResponse.response() != null
                    && midTermResponse.response().body() != null
                    && midTermResponse.response().body().items() != null
                    && midTermResponse.response().body().items().item() != null
                    && !midTermResponse.response().body().items().item().isEmpty()) {

                KmaMidWeatherApiResponse.Item item = midTermResponse.response().body().items().item().get(0);
                LocalDate fcstBaseDate = LocalDate.parse(tmFc.substring(0, 8), DATE_FMT);

                // 중기 예보는 발표일 기준 Day 3 ~ Day 7 (단기예보와 겹치지 않는 날짜만 추가)
                addMidTermIfNotPresent(combined, shortTermDates, extractMidTerm(item, fcstBaseDate, 3, item.wf3Am(), item.wf3Pm()));
                addMidTermIfNotPresent(combined, shortTermDates, extractMidTerm(item, fcstBaseDate, 4, item.wf4Am(), item.wf4Pm()));
                addMidTermIfNotPresent(combined, shortTermDates, extractMidTerm(item, fcstBaseDate, 5, item.wf5Am(), item.wf5Pm()));
                addMidTermIfNotPresent(combined, shortTermDates, extractMidTerm(item, fcstBaseDate, 6, item.wf6Am(), item.wf6Pm()));
                addMidTermIfNotPresent(combined, shortTermDates, extractMidTerm(item, fcstBaseDate, 7, item.wf7Am(), item.wf7Pm()));
            }

        } catch (Exception e) {
            log.warn("중기예보 병합 실패: {}", e.getMessage());
        }

        return combined;
    }

    private void addMidTermIfNotPresent(List<WeatherForecastResponse> combined, Set<String> shortTermDates, List<WeatherForecastResponse> midTerms) {
        for (WeatherForecastResponse mt : midTerms) {
            if (!shortTermDates.contains(mt.date())) {
                combined.add(mt);
            }
        }
    }

    /**
     * 기상청 중기육상예보는 1일 2회(06:00, 18:00) 발표되며 최근 24시간 이내 데이터만 조회 가능합니다.
     * 공공데이터포털 반영 지연(약 20~30분)을 고려해 06:30, 18:30을 전환 시점으로 적용합니다.
     */
    public String calculateLatestMidTermTmFc(LocalDateTime now) {
        LocalDate date = now.toLocalDate();
        LocalTime time = now.toLocalTime();

        if (time.isBefore(LocalTime.of(6, 30))) {
            // 06:30 이전: 전일 18시 발표
            return date.minusDays(1).format(DATE_FMT) + "1800";
        } else if (time.isBefore(LocalTime.of(18, 30))) {
            // 06:30 ~ 18:30: 당일 06시 발표
            return date.format(DATE_FMT) + "0600";
        } else {
            // 18:30 이후: 당일 18시 발표
            return date.format(DATE_FMT) + "1800";
        }
    }

    private List<WeatherForecastResponse> extractMidTerm(KmaMidWeatherApiResponse.Item item, LocalDate baseDate, int plusDays, String wfAm, String wfPm) {
        List<WeatherForecastResponse> list = new ArrayList<>();
        String targetDate = baseDate.plusDays(plusDays).format(DATE_FMT);

        // 오전(AM)은 1000으로 매핑
        if (wfAm != null && !wfAm.isBlank()) {
            list.add(new WeatherForecastResponse(targetDate, "1000", mapWfToStatus(wfAm), 0.0));
        }
        // 오후(PM)는 1400, 1800으로 매핑
        if (wfPm != null && !wfPm.isBlank()) {
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
     * 위도/경도를 기반으로 기상청 중기육상예보 구역코드(regId)를 매핑합니다.
     */
    public String getMidTermRegionCode(Double lat, Double lng) {
        if (lat == null || lng == null) {
            return "11B00000"; // 기본값 서울/경기
        }

        // 1. 제주도
        if (lat < 34.0 || (lat < 34.4 && lng < 127.0)) {
            return "11G00000";
        }

        // 2. 남부 지방 (호남 / 영남)
        if (lat < 36.0) {
            if (lng < 127.7) {
                // 호남: 전남(11F20000), 전북(11F10000)
                return (lat < 35.3) ? "11F20000" : "11F10000";
            } else {
                // 영남: 경남/부산/울산(11H20000), 대구/경북(11H10000)
                return (lat < 35.6) ? "11H20000" : "11H10000";
            }
        }

        // 3. 중부 지방 (충청 / 경북 북부)
        if (lat < 37.0) {
            if (lng < 127.3) {
                return "11C20000"; // 대전, 세종, 충남
            } else if (lng < 128.3) {
                return "11C10000"; // 충북
            } else {
                return "11H10000"; // 경북 북부 (안동, 영주, 문경 등)
            }
        }

        // 4. 북부 지방 (강원 영동 / 강원 영서 / 수도권)
        // 강원 영동: 속초, 고성, 양양, 강릉, 동해, 삼척 등 태백산맥 동쪽 (lng >= 128.5)
        if (lng >= 128.5) {
            return "11D20000";
        }
        // 강원 영서: 춘천, 원주, 홍천, 횡성, 철원, 화천 등 (lng >= 127.5 또는 철원 등 북부 경도 >= 127.1)
        if (lng >= 127.5 || (lat >= 38.0 && lng >= 127.1)) {
            return "11D10000";
        }

        // 수도권 (서울, 인천, 경기)
        return "11B00000";
    }
}
