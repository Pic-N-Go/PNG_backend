package com.project.picngo.community.dto;

import com.project.picngo.common.image.dto.PhotoExifInfo;

public record ImageUploadResponse(
        Long imageId,
        String imageUrl,
        PhotoExifInfo exif
) {
}
