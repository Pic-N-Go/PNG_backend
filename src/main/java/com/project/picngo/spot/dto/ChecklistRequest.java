package com.project.picngo.spot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChecklistRequest(
        @NotBlank
        @Size(max = 20, message = "체크리스트 항목은 20자 이하여야 합니다.")
        String content
) {}
