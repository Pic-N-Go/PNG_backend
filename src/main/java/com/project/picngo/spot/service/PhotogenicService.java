package com.project.picngo.spot.service;

import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.SpotErrorCode;
import com.project.picngo.external.AirQualityClient;
import com.project.picngo.external.dto.AirQualityResponse.Item;
import com.project.picngo.spot.domain.SeasonEvent;
import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.dto.PhotogenicResponse;
import com.project.picngo.spot.dto.PhotogenicResponse.FactorInfo;
import com.project.picngo.spot.repository.SeasonEventRepository;
import com.project.picngo.spot.repository.SpotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.MonthDay;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PhotogenicService {

    private static final DateTimeFormatter MM_DD = DateTimeFormatter.ofPattern("MM-dd");

    private final SpotRepository spotRepository;
    private final SeasonEventRepository seasonEventRepository;
    private final AirQualityClient airQualityClient;

    public PhotogenicResponse calculate(Long spotId) {
        Spot spot = spotRepository.findById(spotId)
                .orElseThrow(() -> new CustomException(SpotErrorCode.SPOT_NOT_FOUND));

        String region = extractRegion(spot.getAddress());
        FactorInfo seasonFactor = calculateSeason(region);

        Item air = airQualityClient.getAirQuality(region);
        FactorInfo fineDustFactor = calculateFineDust(air);
        FactorInfo ozoneFactor = calculateOzone(air);

        // ponytail: 날씨/골든아워는 모정민 영역, 연동 후 채움
        FactorInfo weatherFactor = new FactorInfo("구현 예정", 0);
        FactorInfo goldenHourFactor = new FactorInfo("구현 예정", 0);

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

    private FactorInfo calculateSeason(String region) {
        List<SeasonEvent> events = seasonEventRepository.findActiveByRegion(region);

        MonthDay today = MonthDay.now();

        return events.stream()
                .filter(e -> isInRange(today, MonthDay.parse(e.getMonthDayStart(), MM_DD),
                        MonthDay.parse(e.getMonthDayEnd(), MM_DD)))
                .findFirst()
                .map(e -> {
                    int score = calculateSeasonScore(today, e);
                    int pct = (int) Math.round(score * 100.0 / e.getMaxScore());
                    return new FactorInfo(e.getName() + " " + pct + "%", score);
                })
                .orElse(new FactorInfo("해당 없음", 0));
    }

    private int calculateSeasonScore(MonthDay today, SeasonEvent event) {
        MonthDay peakStart = MonthDay.parse(event.getMonthDayPeakStart(), MM_DD);
        MonthDay peakEnd = MonthDay.parse(event.getMonthDayPeakEnd(), MM_DD);

        if (!today.isBefore(peakStart) && !today.isAfter(peakEnd)) {
            return event.getMaxScore(); // 피크 구간 = 만점
        }
        // 피크 바깥: 절반 점수
        return event.getMaxScore() / 2;
    }

    private boolean isInRange(MonthDay target, MonthDay start, MonthDay end) {
        return !target.isBefore(start) && !target.isAfter(end);
    }

    // ponytail: 에어코리아 sidoName은 "서울", "부산" 등 단축형만 허용
    private String extractRegion(String address) {
        if (address == null) return "";
        String first = address.split(" ")[0];
        return first.replace("특별시", "").replace("광역시", "").replace("특별자치시", "").replace("특별자치도", "");
    }
}
