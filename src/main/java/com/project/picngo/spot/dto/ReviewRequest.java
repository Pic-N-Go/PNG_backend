package com.project.picngo.spot.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record ReviewRequest(
        @NotNull @Min(1) @Max(5) Integer rating,
        @NotBlank String content,
        String equipmentInfo,
        LocalDate visitedAt
) {}
