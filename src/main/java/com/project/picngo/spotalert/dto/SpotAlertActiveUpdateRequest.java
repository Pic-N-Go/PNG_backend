package com.project.picngo.spotalert.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotNull;

public record SpotAlertActiveUpdateRequest(
    @NotNull(message = "알림 활성화 여부는 필수 입력값입니다.")
    @JsonAlias("isActive")
    Boolean isAlertEnabled
) {}
