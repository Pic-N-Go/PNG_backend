package com.project.picngo.course.agent.dto;

import com.project.picngo.common.domain.SpotCategory;

import java.time.LocalDate;
import java.util.List;

public record PlanGoal(
        String region,
        LocalDate targetDate,
        List<SpotCategory> categories,
        String theme,
        List<String> keywords
) {
    public static PlanGoal fallback(String prompt, String region, LocalDate targetDate) {
        String resolvedRegion = (region != null && !region.isBlank()) ? region : "서울";
        LocalDate resolvedDate = (targetDate != null) ? targetDate : LocalDate.now().plusDays(1);
        return new PlanGoal(
                resolvedRegion,
                resolvedDate,
                List.of(SpotCategory.SUNRISE_SUNSET, SpotCategory.NIGHT_VIEW, SpotCategory.PARK),
                "감성 출사 추천 코스",
                List.of("노을", "야경", "산책")
        );
    }
}
