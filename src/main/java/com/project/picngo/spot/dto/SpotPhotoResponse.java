package com.project.picngo.spot.dto;

import com.project.picngo.spot.domain.SpotPhoto;

import java.util.List;

public record SpotPhotoResponse(
        Long spotId,
        List<PhotoInfo> photos
) {
    // imgName은 저장 안 함 → 항상 null (프론트 미사용 확인되면 필드 제거)
    public record PhotoInfo(
            String originUrl,
            String thumbnailUrl,
            String imgName
    ) {
        public static PhotoInfo from(SpotPhoto photo) {
            return new PhotoInfo(photo.getPhotoUrl(), photo.getThumbnailUrl(), null);
        }
    }

    public static SpotPhotoResponse of(Long spotId, List<SpotPhoto> photos) {
        return new SpotPhotoResponse(
                spotId,
                photos.stream().map(PhotoInfo::from).toList()
        );
    }
}
