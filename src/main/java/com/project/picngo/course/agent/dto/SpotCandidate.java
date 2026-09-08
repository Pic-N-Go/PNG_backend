package com.project.picngo.course.agent.dto;

import com.project.picngo.common.domain.SpotCategory;

public record SpotCandidate(
        Long id,
        String name,
        String address,
        Double latitude,
        Double longitude,
        SpotCategory category,
        String overview
) {}
