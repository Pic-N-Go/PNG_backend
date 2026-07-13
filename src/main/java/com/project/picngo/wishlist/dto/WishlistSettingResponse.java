package com.project.picngo.wishlist.dto;

import com.project.picngo.wishlist.domain.enums.TimeCondition;
import com.project.picngo.wishlist.domain.enums.WeatherCondition;

import java.time.LocalTime;
import java.util.List;
import java.util.Set;

public record WishlistSettingResponse(
    Long spotId,
    String spotName,
    String address,
    Integer photogenicScore,
    List<String> tags,
    
    String memo,
    Set<WeatherCondition> weatherConditions,
    Set<TimeCondition> timeConditions,
    
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
