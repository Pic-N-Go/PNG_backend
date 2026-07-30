package com.project.picngo.spotalert.dto;

import com.project.picngo.spotalert.domain.enums.TimeCondition;
import com.project.picngo.spotalert.domain.enums.WeatherCondition;
import com.project.picngo.spotalert.domain.enums.AirQualityCondition;

import java.time.LocalTime;
import java.util.List;
import java.util.Set;

public record SpotAlertSettingResponse(
    Long spotId,
    String spotName,
    String address,
    Integer photogenicScore,
    List<String> tags,
    
    String memo,
    Set<WeatherCondition> weatherConditions,
    Set<TimeCondition> timeConditions,
    AirQualityCondition airQualityCondition,
    
    Boolean isAlertEnabled,
    Integer alertTimingDays,
    LocalTime dndStartTime,
    LocalTime dndEndTime,
    
    List<ExpectedMatchDayDto> expectedMatchDays
) {
    public record ExpectedMatchDayDto(
        String dayLabel,
        String date,
        String weatherStatus,
        Boolean isMatched
    ) {}
}
