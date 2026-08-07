package com.project.picngo.album.dto;

import com.project.picngo.common.domain.SpotCategory;

public record AlbumCreateRequest(
        String name,
        SpotCategory category,
        boolean isPublic
){
}
