package com.project.picngo.spot.service;

import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.SpotErrorCode;
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

    public PhotogenicResponse calculate(Long spotId) {
        Spot spot = spotRepository.findById(spotId)
                .orElseThrow(() -> new CustomException(SpotErrorCode.SPOT_NOT_FOUND));

        FactorInfo seasonFactor = calculateSeason(spot.getAddress());

        // ponytail: 날씨/미세먼지/골든아워는 외부 API 연동 후 채움. 임시 고정값.
        FactorInfo weatherFactor = new FactorInfo("구현 예정", 0);
        FactorInfo fineDustFactor = new FactorInfo("구현 예정", 0);
        FactorInfo goldenHourFactor = new FactorInfo("구현 예정", 0);

        int total = weatherFactor.score() + fineDustFactor.score()
                + seasonFactor.score() + goldenHourFactor.score();

        return new PhotogenicResponse(
                total,
                PhotogenicResponse.gradeFrom(total),
                weatherFactor,
                fineDustFactor,
                seasonFactor,
                goldenHourFactor
        );
    }

    private FactorInfo calculateSeason(String address) {
        String region = extractRegion(address);
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

    // ponytail: 주소 앞 단어로 지역 추출 (예: "부산 해운대구..." → "부산")
    private String extractRegion(String address) {
        if (address == null) return null;
        return address.split(" ")[0];
    }
}
