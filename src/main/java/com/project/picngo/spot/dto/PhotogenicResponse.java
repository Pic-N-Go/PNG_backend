package com.project.picngo.spot.dto;

public record PhotogenicResponse(
        int score,
        String grade,
        FactorInfo weather,
        FactorInfo fineDust,
        FactorInfo ozone,
        FactorInfo season,
        FactorInfo goldenHour
) {
    public record FactorInfo(String label, int score) {}

    public static String gradeFrom(int score) {
        if (score >= 80) return "매우 좋음";
        if (score >= 60) return "좋음";
        if (score >= 40) return "보통";
        return "비추천";
    }
}
