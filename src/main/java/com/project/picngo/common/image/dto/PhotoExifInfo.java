package com.project.picngo.common.image.dto;

import java.time.LocalDateTime;

public record PhotoExifInfo(
        Double latitude,
        Double longitude,
        LocalDateTime takenAt,

        String cameraMake,
        String cameraModel,
        String lensMake,
        String lensModel,
        String software,

        Integer iso,
        String exposureTime,
        String shutterSpeed,
        String fNumber,
        String focalLength,
        String focalLength35mm,
        String flash,
        String whiteBalance,
        String meteringMode,
        String exposureMode,
        String digitalZoomRatio,

        Integer imageWidth,
        Integer imageHeight,
        String colorSpace,
        String fileFormat,

        String maxApertureValue,
        String subjectDistance,

        String author,
        String copyright,
        String imageDescription,
        String caption,

        String fileName,
        Long fileSize
) {
}
