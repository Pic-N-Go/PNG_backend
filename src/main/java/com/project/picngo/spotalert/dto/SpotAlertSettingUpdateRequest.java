package com.project.picngo.spotalert.dto;

import com.project.picngo.spotalert.domain.enums.TimeCondition;
import com.project.picngo.spotalert.domain.enums.WeatherCondition;
import com.project.picngo.spotalert.domain.enums.AirQualityCondition;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;
import java.util.Set;

public record SpotAlertSettingUpdateRequest(
    @Size(max = 200, message = "메모는 200자 이내여야 합니다.")
    String memo,
    Set<WeatherCondition> weatherConditions,
    Set<TimeCondition> timeConditions,
    AirQualityCondition airQualityCondition,
    Boolean isAlertEnabled,
    Integer alertTimingDays,
    LocalTime dndStartTime,
    LocalTime dndEndTime
) {}
