package com.project.picngo.album.dto;

import com.project.picngo.album.domain.Album;
import com.project.picngo.album.domain.AlbumPhoto;
import com.project.picngo.common.domain.SpotCategory;

import java.util.List;

public record AlbumDetailResponse (
        Long id,
        String name,
        SpotCategory category,
        boolean isPublic,
        List<AlbumPhotoResponse> photos
) {

    public static AlbumDetailResponse from(Album album, List<AlbumPhoto> photos){
        return new AlbumDetailResponse(
                album.getId(),
                album.getName(),
                album.getCategory(),
                album.isPublic(),
                photos.stream()
                        .map(AlbumPhotoResponse::from)
                        .toList()
        );
    }
}
