package com.project.picngo.spot.service;

import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.SpotErrorCode;
import com.project.picngo.external.SidoNameMapper;
import com.project.picngo.external.dto.AirQualityResponse.Item;
import com.project.picngo.external.dto.GoldenHourResponse;
import com.project.picngo.external.dto.WeatherForecastResponse;
import com.project.picngo.external.service.WeatherCacheService;
import com.project.picngo.spot.domain.SeasonEvent;
import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.dto.PhotogenicResponse;
import com.project.picngo.spot.dto.PhotogenicResponse.FactorInfo;
import com.project.picngo.spot.dto.PhotogenicResponse.GoldenHourInfo;
import com.project.picngo.spot.repository.SeasonEventRepository;
import com.project.picngo.spot.repository.SpotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PhotogenicService {

    private static final DateTimeFormatter MM_DD = DateTimeFormatter.ofPattern("MM-dd");
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HHmm");
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private static final int NON_LEAP_YEAR = 2001; // MonthDay 거리 계산용 임의 기준 연도 (윤년 2/29 이슈 회피)

    private final SpotRepository spotRepository;
    private final SeasonEventRepository seasonEventRepository;
    private final WeatherCacheService weatherCacheService;

    public PhotogenicResponse calculate(Long spotId, LocalDate date, LocalTime time) {
        LocalDate resolvedDate = date != null ? date : LocalDate.now(KST);
        LocalTime resolvedTime = time != null ? time : LocalTime.now(KST);

        Spot spot = spotRepository.findById(spotId)
                .orElseThrow(() -> new CustomException(SpotErrorCode.SPOT_NOT_FOUND));

        String region = SidoNameMapper.normalize(spot.getAddress());
        FactorInfo seasonFactor = calculateSeason(region, spot.getCat3(), resolvedDate);

        Item air = null;
        if (region != null) {
            try {
                air = weatherCacheService.getCachedAirQuality(region);
            } catch (Exception e) {
                log.warn("에어코리아 API 호출 실패, 대기질 점수 0점 처리: {}", e.getMessage());
            }
        }
        FactorInfo fineDustFactor = calculateFineDust(air);
        FactorInfo ozoneFactor = calculateOzone(air);

        FactorInfo weatherFactor = calculateWeather(spot.getLatitude(), spot.getLongitude(), resolvedDate, resolvedTime);
        GoldenHourInfo goldenHourFactor = calculateGoldenHour(spot.getLatitude(), spot.getLongitude(), resolvedDate, resolvedTime);

        int total = weatherFactor.score() + fineDustFactor.score()
                + ozoneFactor.score() + seasonFactor.score() + goldenHourFactor.score();

        return new PhotogenicResponse(
                total,
                PhotogenicResponse.gradeFrom(total),
                weatherFactor,
                fineDustFactor,
                ozoneFactor,
                seasonFactor,
                goldenHourFactor
        );
    }

    // 날씨 만점 30점 — 선택한 시각에 가장 가까운 예보 슬롯 기준
    private FactorInfo calculateWeather(Double lat, Double lng, LocalDate date, LocalTime time) {
        try {
            String dateStr = date.format(DATE_FMT);
            List<WeatherForecastResponse> forecasts = weatherCacheService.getCached7DayForecast(lat, lng, dateStr);
            if (forecasts == null || forecasts.isEmpty()) return new FactorInfo("데이터 없음", 0);

            WeatherForecastResponse closest = forecasts.stream()
                    .filter(f -> dateStr.equals(f.date()))
                    .min(Comparator.comparingLong(f -> {
                        long diff = Math.abs(Duration.between(LocalTime.parse(f.time(), HHMM), time).toMinutes());
                        return Math.min(diff, 1440 - diff);
                    }))
                    .orElse(null);
            if (closest == null) return new FactorInfo("데이터 없음", 0);

            return switch (closest.weatherStatus()) {
                case "CLEAR"  -> new FactorInfo("맑음", 30);
                case "CLOUDY" -> new FactorInfo("흐림", 15);
                case "SNOWY"  -> new FactorInfo("눈", 10);
                case "RAINY"  -> new FactorInfo("비", 5);
                default       -> new FactorInfo("데이터 없음", 0);
            };
        } catch (Exception e) {
            log.warn("날씨 API 호출 실패, 0점 처리: {}", e.getMessage());
            return new FactorInfo("데이터 없음", 0);
        }
    }

    // 골든아워 만점 5점 — 일출/일몰 전후 30분 이내
    private GoldenHourInfo calculateGoldenHour(Double lat, Double lng, LocalDate date, LocalTime time) {
        try {
            GoldenHourResponse goldenHour = weatherCacheService.getCachedGoldenHour(lat, lng, date.toString());
            if (goldenHour == null) return new GoldenHourInfo("데이터 없음", 0, null, null);
            if (goldenHour.sunriseTime() == null || goldenHour.sunsetTime() == null) {
                return new GoldenHourInfo("데이터 없음", 0, null, null);
            }

            LocalTime sunrise = OffsetDateTime.parse(goldenHour.sunriseTime()).atZoneSameInstant(KST).toLocalTime();
            LocalTime sunset = OffsetDateTime.parse(goldenHour.sunsetTime()).atZoneSameInstant(KST).toLocalTime();

            LocalTime morningStart = sunrise.minusMinutes(30);
            LocalTime morningEnd = sunrise.plusMinutes(30);
            LocalTime eveningStart = sunset.minusMinutes(30);
            LocalTime eveningEnd = sunset.plusMinutes(30);

            if (!time.isBefore(morningStart) && !time.isAfter(morningEnd)) {
                return new GoldenHourInfo("골든아워", 5, null, morningStart.format(HH_MM));
            }
            if (!time.isBefore(eveningStart) && !time.isAfter(eveningEnd)) {
                return new GoldenHourInfo("골든아워", 5, null, eveningStart.format(HH_MM));
            }
            if (time.isBefore(morningStart)) {
                long minutes = Duration.between(time, morningStart).toMinutes();
                return new GoldenHourInfo("해당 없음", 0, (int) minutes, morningStart.format(HH_MM));
            }
            if (time.isBefore(eveningStart)) {
                long minutes = Duration.between(time, eveningStart).toMinutes();
                return new GoldenHourInfo("해당 없음", 0, (int) minutes, eveningStart.format(HH_MM));
            }
            return new GoldenHourInfo("해당 없음", 0, null, null);
        } catch (Exception e) {
            log.warn("골든아워 API 호출 실패, 0점 처리: {}", e.getMessage());
            return new GoldenHourInfo("데이터 없음", 0, null, null);
        }
    }

    // 미세먼지 만점 20점 — grade 없으면 pm10Value(㎍/㎥)로 직접 판단
    private FactorInfo calculateFineDust(Item air) {
        if (air == null || air.pm10Value() == null || air.pm10Value().equals("-")) {
            return new FactorInfo("데이터 없음", 0);
        }
        String grade = nullSafe(air.pm10Grade());
        if (grade.isEmpty()) {
            try {
                int val = Integer.parseInt(air.pm10Value());
                grade = val <= 30 ? "1" : val <= 80 ? "2" : val <= 150 ? "3" : "4";
            } catch (NumberFormatException e) {
                return new FactorInfo("데이터 없음", 0);
            }
        }
        return switch (grade) {
            case "1" -> new FactorInfo("좋음", 20);
            case "2" -> new FactorInfo("보통", 12);
            case "3" -> new FactorInfo("나쁨", 4);
            default  -> new FactorInfo("매우나쁨", 0);
        };
    }

    // 오존 만점 10점 — grade 없으면 o3Value(ppm)로 직접 판단
    private FactorInfo calculateOzone(Item air) {
        if (air == null || air.o3Value() == null || air.o3Value().equals("-")) {
            return new FactorInfo("데이터 없음", 0);
        }
        String grade = nullSafe(air.o3Grade());
        if (grade.isEmpty()) {
            try {
                double val = Double.parseDouble(air.o3Value());
                grade = val <= 0.030 ? "1" : val <= 0.090 ? "2" : val <= 0.150 ? "3" : "4";
            } catch (NumberFormatException e) {
                return new FactorInfo("데이터 없음", 0);
            }
        }
        return switch (grade) {
            case "1" -> new FactorInfo("좋음", 10);
            case "2" -> new FactorInfo("보통", 6);
            case "3" -> new FactorInfo("나쁨", 2);
            default  -> new FactorInfo("매우나쁨", 0);
        };
    }

    private String nullSafe(String val) {
        return val != null ? val : "";
    }

    private FactorInfo calculateSeason(String region, String cat3, LocalDate date) {
        List<SeasonEvent> events = seasonEventRepository.findActiveByRegion(region);
        MonthDay today = MonthDay.from(date);

        return events.stream()
                .filter(e -> e.isEligibleForCat3(cat3))
                .filter(e -> isInRange(today, MonthDay.parse(e.getMonthDayStart(), MM_DD),
                        MonthDay.parse(e.getMonthDayEnd(), MM_DD)))
                // 겹치는 이벤트 중 오늘이 "피크 구간"인 쪽을 우선, 그 다음 피크 중심일과 가까운 쪽을 우선
                // (findActiveByRegion에 ORDER BY가 없어 row 순서에만 의존하면 겹치는 기간에 엉뚱한 이벤트가 뽑힘)
                .min(Comparator
                        .comparing((SeasonEvent e) -> isInRange(today, MonthDay.parse(e.getMonthDayPeakStart(), MM_DD),
                                MonthDay.parse(e.getMonthDayPeakEnd(), MM_DD)) ? 0 : 1)
                        .thenComparing(e -> peakCenterDistance(today, e)))
                .map(e -> {
                    int score = calculateSeasonScore(today, e);
                    int pct = (int) Math.round(score * 100.0 / e.getMaxScore());
                    return new FactorInfo(e.getName() + " " + pct + "%", score);
                })
                .orElse(new FactorInfo("해당 없음", 0));
    }

    private long peakCenterDistance(MonthDay today, SeasonEvent event) {
        LocalDate refToday = today.atYear(NON_LEAP_YEAR);
        LocalDate peakStart = MonthDay.parse(event.getMonthDayPeakStart(), MM_DD).atYear(NON_LEAP_YEAR);
        LocalDate peakEnd = MonthDay.parse(event.getMonthDayPeakEnd(), MM_DD).atYear(NON_LEAP_YEAR);
        LocalDate peakCenter = peakStart.plusDays(ChronoUnit.DAYS.between(peakStart, peakEnd) / 2);
        return Math.abs(ChronoUnit.DAYS.between(refToday, peakCenter));
    }

    private int calculateSeasonScore(MonthDay today, SeasonEvent event) {
        MonthDay peakStart = MonthDay.parse(event.getMonthDayPeakStart(), MM_DD);
        MonthDay peakEnd = MonthDay.parse(event.getMonthDayPeakEnd(), MM_DD);

        if (!today.isBefore(peakStart) && !today.isAfter(peakEnd)) {
            return event.getMaxScore();
        }
        return event.getMaxScore() / 2;
    }

    private boolean isInRange(MonthDay target, MonthDay start, MonthDay end) {
        return !target.isBefore(start) && !target.isAfter(end);
    }
}
