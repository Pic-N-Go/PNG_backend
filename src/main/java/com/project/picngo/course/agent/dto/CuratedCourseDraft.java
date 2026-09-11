package com.project.picngo.course.agent.dto;

import java.util.List;

public record CuratedCourseDraft(
        String title,
        String theme,
        String overview,
        int durationDays,
        List<CuratedSpotItem> spots
) {
    public CuratedCourseDraft(String title, String theme, String overview, List<CuratedSpotItem> spots) {
        this(title, theme, overview, 1, spots);
    }
}
