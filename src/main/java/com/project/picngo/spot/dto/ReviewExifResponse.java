package com.project.picngo.spot.dto;

import com.project.picngo.common.image.dto.PhotoExifResponse;

import java.util.List;

public record ReviewExifResponse(
        Long reviewId,
        List<PhotoExifResponse> images
) {
}
