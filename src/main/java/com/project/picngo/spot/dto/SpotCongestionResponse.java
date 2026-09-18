package com.project.picngo.spot.dto;

import java.util.List;

public record SpotCongestionResponse(
        Long spotId,
        String spotName,
        String matchedAttractionName,
        boolean hasData,
        CongestionDayInfo bestCleanDay,
        CongestionDayInfo targetDay,
        List<CongestionDayInfo> days
) {
    public record CongestionDayInfo(
            String date,
            String dayOfWeek,
            double rate,
            String level,
            String levelLabel
    ) {}

    public static String levelFrom(double rate) {
        if (rate <= 35.0) return "RELAXED";
        if (rate <= 60.0) return "NORMAL";
        if (rate <= 80.0) return "CROWDED";
        return "VERY_CROWDED";
    }

    public static String levelLabelFrom(String level) {
        return switch (level) {
            case "RELAXED" -> "여유";
            case "NORMAL" -> "보통";
            case "CROWDED" -> "혼잡";
            case "VERY_CROWDED" -> "매우 혼잡";
            default -> "보통";
        };
    }

    public static SpotCongestionResponse empty(Long spotId, String spotName) {
        return new SpotCongestionResponse(spotId, spotName, null, false, null, null, List.of());
    }
}
