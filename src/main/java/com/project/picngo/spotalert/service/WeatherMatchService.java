package com.project.picngo.spotalert.service;

import com.project.picngo.external.dto.WeatherForecastResponse;
import com.project.picngo.spotalert.domain.enums.TimeCondition;
import com.project.picngo.spotalert.domain.enums.WeatherCondition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * 출사알림의 '촬영 시간대(TimeCondition) + 원하는 날씨(WeatherCondition)'가
 * 특정 날짜의 예보와 일치하는지 판정하는 공용 컴포넌트.
 * <p>
 * 스케줄러(알림 발송 판단)와 출사알림 상세 프리뷰(화면 표시 판단)가 동일한 규칙을 쓰도록
 * 매칭 로직을 이 한 곳으로 모은다.
 * <p>
 * <b>데이터 기반 정밀도 분기</b><br>
 * - 단기예보(1~3일)는 시간별 데이터가 있으므로 시간대 윈도우로 <b>정밀 매칭</b>한다.<br>
 * - 중기예보(3~7일)는 오전/오후(AM/PM)만 제공하므로, 시간대를 반나절 슬롯으로 <b>근사 매칭</b>한다.
 * (기상청 오전/오후 예보 정의와 일치: 새벽·오전 → 오전, 오후·야간 → 오후)
 */
@Slf4j
@Component
public class WeatherMatchService {

    /**
     * 특정 날짜(targetDateStr, yyyyMMdd)의 예보 중 유저의 시간대(timeCondition)에 해당하는 슬롯을 골라
     * 유저가 원하는 날씨 조건(userConditions) 중 하나라도 일치하면 true.
     */
    public boolean matches(List<WeatherForecastResponse> forecast, String targetDateStr,
                           TimeCondition timeCondition, Set<WeatherCondition> userConditions) {
        if (userConditions == null || userConditions.isEmpty()) return false;
        if (userConditions.contains(WeatherCondition.NONE)) return true;

        for (WeatherForecastResponse slot : slotsForMatching(forecast, targetDateStr, timeCondition)) {
            WeatherCondition apiWeather = parse(slot.weatherStatus());
            if (apiWeather != null && userConditions.contains(apiWeather)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 시간대 지정이 없는 출사알림용: 해당 날짜의 모든 슬롯 중 하나라도 조건에 맞으면 true.
     * (프리뷰에서 timeConditions가 비어 있는 경우의 Fallback)
     */
    public boolean matchesAnyTime(List<WeatherForecastResponse> forecast, String targetDateStr,
                                  Set<WeatherCondition> userConditions) {
        if (userConditions == null || userConditions.isEmpty()) return false;
        if (userConditions.contains(WeatherCondition.NONE)) return true;

        return forecast.stream()
                .filter(f -> targetDateStr.equals(f.date()))
                .anyMatch(f -> {
                    WeatherCondition apiWeather = parse(f.weatherStatus());
                    return apiWeather != null && userConditions.contains(apiWeather);
                });
    }

    /**
     * 매칭에 사용할 예보 슬롯을 선별
     * 우선 시간대 윈도우(단기예보 시간별)로 찾고, 해당 슬롯이 없으면 중기예보 AM/PM 슬롯으로 Fallback.
     */
    public List<WeatherForecastResponse> slotsForMatching(List<WeatherForecastResponse> forecast,
                                                          String targetDateStr, TimeCondition timeCondition) {
        List<WeatherForecastResponse> sameDate = forecast.stream()
                .filter(f -> targetDateStr.equals(f.date()))
                .toList();

        int[] window = hourWindow(timeCondition);
        if (window != null) {
            List<WeatherForecastResponse> precise = sameDate.stream()
                    .filter(f -> inHourWindow(f.time(), window[0], window[1]))
                    .toList();
            if (!precise.isEmpty()) {
                return precise; // 단기예보 시간별 데이터 존재 → 정밀 매칭
            }
        }

        // 폴백: 중기예보 AM/PM 합성 슬롯으로 근사 매칭 (주로 DAWN/NIGHT의 원거리 날짜)
        Set<String> amPmSlots = amPmSlots(timeCondition);
        return sameDate.stream()
                .filter(f -> amPmSlots.contains(f.time()))
                .toList();
    }

    /** 시간대별 정밀 매칭 윈도우 [시작시, 종료시] (양끝 포함, 단기예보 시간별 데이터용) */
    private int[] hourWindow(TimeCondition timeCondition) {
        return switch (timeCondition) {
            case DAWN -> new int[]{4, 6};
            case MORNING -> new int[]{7, 11};
            case AFTERNOON -> new int[]{12, 16};
            case NIGHT -> new int[]{19, 23};
            default -> null; // SUNRISE/SUNSET/NONE 등은 이 서비스의 대상이 아님
        };
    }

    /**
     * 중기예보(오전/오후만 존재) Fallback용 슬롯.
     * WeatherForecastService.extractMidTerm이 오전=1000, 오후=1400·1800으로 매핑하므로 그에 맞춘다.
     */
    private Set<String> amPmSlots(TimeCondition timeCondition) {
        return switch (timeCondition) {
            case DAWN, MORNING -> Set.of("1000");            // 오전(AM)
            case AFTERNOON, NIGHT -> Set.of("1400", "1800"); // 오후(PM)
            default -> Set.of();
        };
    }

    /** "HHmm" 형식 시각의 '시(HH)'가 [startHour, endHour] 범위에 드는지 */
    private boolean inHourWindow(String hhmm, int startHour, int endHour) {
        if (hhmm == null || hhmm.length() < 2) return false;
        try {
            int hour = Integer.parseInt(hhmm.substring(0, 2));
            return hour >= startHour && hour <= endHour;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private WeatherCondition parse(String status) {
        try {
            return WeatherCondition.valueOf(status);
        } catch (IllegalArgumentException e) {
            log.warn("알 수 없는 기상청 날씨 상태 수신 (스킵 처리): {}", status);
            return null;
        }
    }
}
