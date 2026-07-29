package com.project.picngo.common.image.dto;

// EXIF 데이터가 필요한 도메인에서 공통으로 응답하는 Response
public record PhotoExifResponse(
        Long imageId,
        String cameraModel,
        String lensModel,
        Integer iso,
        String fNumber,
        String exposureTime,
        String focalLength,
        String exposureMode,
        String meteringMode,
        String whiteBalance,
        String flash,
        String focalLength35mm,
        String software,
        Double latitude,
        Double longitude,
        Long fileSize,
        String fileFormat,
        String fileName
) {
}
