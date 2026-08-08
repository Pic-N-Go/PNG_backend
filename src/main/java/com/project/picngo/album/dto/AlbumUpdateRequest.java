package com.project.picngo.album.dto;

import com.project.picngo.common.domain.SpotCategory;

public record AlbumUpdateRequest(
        String name,
        SpotCategory category,
        boolean isPublic
) {
}
