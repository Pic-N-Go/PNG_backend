package com.project.picngo.contest.dto;

import jakarta.validation.constraints.Size;

public record ContestCreateEntryRequest(

        @Size(max = 80, message = "설명은 최대 80자까지 입력할 수 있습니다.")
        String caption,

        Long spotId,

        @Size(max = 100, message = "장소명은 최대 100자까지 입력할 수 있습니다.")
        String spotName
) {
}
