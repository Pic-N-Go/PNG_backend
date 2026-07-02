package com.project.picngo.spot.service;

import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.SpotErrorCode;
import com.project.picngo.external.TourApiClient;
import com.project.picngo.spot.domain.ChecklistMapper;
import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.domain.SpotTag;
import com.project.picngo.spot.dto.SpotDetailResponse;
import com.project.picngo.spot.dto.SpotPhotoResponse;
import com.project.picngo.spot.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SpotService {

    // ponytail: Spring Security 연동 전까지 하드코딩
    private static final Long TEMP_USER_ID = 1L;

    private final SpotRepository spotRepository;
    private final SpotTagRepository spotTagRepository;
    private final ReviewRepository reviewRepository;
    private final SpotPhotoRepository spotPhotoRepository;
    private final BookmarkRepository bookmarkRepository;
    private final TourApiClient tourApiClient;

    public SpotDetailResponse getSpotDetail(Long spotId) {
        Spot spot = spotRepository.findById(spotId)
                .orElseThrow(() -> new CustomException(SpotErrorCode.SPOT_NOT_FOUND));

        List<SpotTag> tags = spotTagRepository.findBySpotId(spotId);
        List<String> checklist = ChecklistMapper.getChecklist(spot.getCat3());

        Object[] avgAndCount = reviewRepository.findAvgAndCountBySpotId(spotId).get(0);
        Double avgRating = (Double) avgAndCount[0];
        int reviewCount = avgAndCount[1] != null ? ((Long) avgAndCount[1]).intValue() : 0;
        long photoCount = spotPhotoRepository.countBySpotId(spotId);
        boolean isBookmarked = bookmarkRepository.existsBySpotIdAndUserId(spotId, TEMP_USER_ID);

        return SpotDetailResponse.of(
                spot, tags, checklist,
                avgRating != null ? Math.round(avgRating * 10) / 10.0 : 0.0,
                reviewCount, photoCount, isBookmarked
        );
    }

    public SpotPhotoResponse getSpotPhotos(Long spotId) {
        Spot spot = spotRepository.findById(spotId)
                .orElseThrow(() -> new CustomException(SpotErrorCode.SPOT_NOT_FOUND));

        if (spot.getTourContentId() == null) {
            return SpotPhotoResponse.of(spotId, List.of());
        }

        return SpotPhotoResponse.of(spotId, tourApiClient.getDetailImages(spot.getTourContentId()));
    }
}
