package com.project.picngo.spot.dto;

import com.project.picngo.spot.domain.enums.ReviewTag;
import com.project.picngo.spot.domain.enums.TimePeriod;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public record ReviewRequest(
        @NotNull @Min(1) @Max(5)
        Integer rating,

        @NotBlank
        @Size(min = 20, max = 500)
        String content,

        @NotNull
        TimePeriod timePeriod,

        @Size(max = 5)
        Set<ReviewTag> tags,

        @Size(max = 5)
        List<String> equipmentInfo,

        @NotNull
        LocalDate visitedAt
) {}
