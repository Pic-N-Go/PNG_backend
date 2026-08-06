package com.project.picngo.album.dto;

import com.project.picngo.album.domain.Album;
import com.project.picngo.common.domain.SpotCategory;

public record  AlbumResponse (
        Long id,
        String name,
        SpotCategory category,
        boolean isPublic,
        long photoCount
){
    public static AlbumResponse from(Album album, long photoCount){
        return new AlbumResponse(
                album.getId(),
                album.getName(),
                album.getCategory(),
                album.isPublic(),
                photoCount
        );
    }
}
