package com.project.picngo.album.dto;

import com.project.picngo.album.domain.AlbumPhoto;

public record AlbumPhotoResponse (
        Long id,
        String imageUrl
){
    public static AlbumPhotoResponse from(AlbumPhoto albumPhoto, String imageUrl) {
        return new AlbumPhotoResponse(
                albumPhoto.getId(),
                imageUrl
        );
    }
}
