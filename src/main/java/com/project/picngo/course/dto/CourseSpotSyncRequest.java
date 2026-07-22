package com.project.picngo.course.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CourseSpotSyncRequest(
        @Valid @NotNull List<CourseSpotSyncItem> spots
) {}
