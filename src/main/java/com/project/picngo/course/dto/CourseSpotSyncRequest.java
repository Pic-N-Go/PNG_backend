package com.project.picngo.course.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CourseSpotSyncRequest(
        @NotNull Integer dayNumber,
        @Valid @NotNull List<CourseSpotSyncItem> spots
) {}
