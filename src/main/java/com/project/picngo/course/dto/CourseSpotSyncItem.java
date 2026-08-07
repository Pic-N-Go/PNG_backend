package com.project.picngo.course.dto;

import jakarta.validation.constraints.NotNull;

public record CourseSpotSyncItem(
        Long courseSpotId, // 기존 스팟은 ID 존재, 신규 스팟은 null
        @NotNull Long spotId,
        @NotNull Integer dayNumber,
        @NotNull Integer sequenceOrder,
        String memo
) {}
