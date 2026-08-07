package com.project.picngo.community.dto;

import com.project.picngo.common.image.dto.PhotoExifResponse;

import java.util.List;

public record PostExifResponse(
        Long postId,
        List<PhotoExifResponse> images
) {
}
