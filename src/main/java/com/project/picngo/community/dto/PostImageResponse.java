package com.project.picngo.community.dto;

public record PostImageResponse(
        Long id,
        String imageUrl,
        Integer width,
        Integer height
) {
}
