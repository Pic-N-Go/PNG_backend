package com.project.picngo.spot.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record MapBoundsRequest(
        @NotNull(message = "남서쪽 위도는 필수입니다.")
        @DecimalMin(value = "-90.0", message = "위도는 -90.0 이상이어야 합니다.")
        @DecimalMax(value = "90.0", message = "위도는 90.0 이하여야 합니다.")
        Double southWestLat,

        @NotNull(message = "남서쪽 경도는 필수입니다.")
        @DecimalMin(value = "-180.0", message = "경도는 -180.0 이상이어야 합니다.")
        @DecimalMax(value = "180.0", message = "경도는 180.0 이하여야 합니다.")
        Double southWestLng,

        @NotNull(message = "북동쪽 위도는 필수입니다.")
        @DecimalMin(value = "-90.0", message = "위도는 -90.0 이상이어야 합니다.")
        @DecimalMax(value = "90.0", message = "위도는 90.0 이하여야 합니다.")
        Double northEastLat,

        @NotNull(message = "북동쪽 경도는 필수입니다.")
        @DecimalMin(value = "-180.0", message = "경도는 -180.0 이상이어야 합니다.")
        @DecimalMax(value = "180.0", message = "경도는 180.0 이하여야 합니다.")
        Double northEastLng,

        String category,

        @jakarta.validation.constraints.Min(value = 1, message = "크기는 1 이상이어야 합니다.")
        @jakarta.validation.constraints.Max(value = 200, message = "크기는 200 이하여야 합니다.")
        @io.swagger.v3.oas.annotations.Parameter(description = "반환할 최대 핀 개수 (기본값 100, 최대 200)")
        Integer size
) {
    public int getSizeOrDefault() {
        return size == null ? 100 : size;
    }
}
