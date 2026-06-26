package com.project.picngo.spot.dto;

import com.project.picngo.external.dto.TourApiImageResponse.ImageItem;

import java.util.List;

public record SpotPhotoResponse(
        Long spotId,
        List<PhotoInfo> photos
) {
    public record PhotoInfo(
            String originUrl,
            String thumbnailUrl,
            String imgName
    ) {
        public static PhotoInfo from(ImageItem item) {
            return new PhotoInfo(item.originimgurl(), item.smallimageurl(), item.imgname());
        }
    }

    public static SpotPhotoResponse of(Long spotId, List<ImageItem> items) {
        return new SpotPhotoResponse(
                spotId,
                items.stream().map(PhotoInfo::from).toList()
        );
    }
}
