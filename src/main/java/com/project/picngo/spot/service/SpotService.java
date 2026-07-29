package com.project.picngo.spot.service;

import com.project.picngo.bookmark.repository.BookmarkCollectionSpotRepository;
import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.SpotErrorCode;
import com.project.picngo.spot.domain.ChecklistMapper;
import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.domain.SpotTag;
import com.project.picngo.spot.domain.enums.ReviewTag;
import com.project.picngo.spot.domain.SpotCategory;
import com.project.picngo.spot.domain.enums.SpotStatus;
import com.project.picngo.spot.dto.NearbySpotResponse;
import com.project.picngo.spot.dto.RecommendedSpotResponse;
import com.project.picngo.spot.dto.SpotDetailResponse;
import com.project.picngo.spot.dto.SpotPhotoResponse;
import com.project.picngo.spot.dto.SpotMapResponse;
import com.project.picngo.spot.dto.SpotResponse;
import com.project.picngo.spot.dto.SpotSummaryResponse;
import com.project.picngo.spot.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SpotService {

    private static final int MAX_PAGE_SIZE = 50;

    private final SpotRepository spotRepository;
    private final SpotTagRepository spotTagRepository;
    private final ReviewRepository reviewRepository;
    private final SpotPhotoRepository spotPhotoRepository;
    private final BookmarkCollectionSpotRepository bookmarkCollectionSpotRepository;

    public SpotDetailResponse getSpotDetail(Long spotId, Long userId) {
        Spot spot = spotRepository.findById(spotId)
                .orElseThrow(() -> new CustomException(SpotErrorCode.SPOT_NOT_FOUND));

        List<SpotTag> tags = spotTagRepository.findBySpotId(spotId);
        // 2회 이상 쓰인 태그 중 상위 3개만 노출 (스팟 상세 카드에 한 줄로 들어가는 분량)
        List<String> reviewTags = reviewRepository.findFrequentTagsBySpotId(spotId).stream()
                .limit(3)
                .map(row -> ((ReviewTag) row[0]).name())
                .toList();
        List<String> checklist = ChecklistMapper.getChecklist(spot.getCat3());

        List<Object[]> rows = reviewRepository.findAvgAndCountBySpotId(spotId);
        Double avgRating = rows.isEmpty() || rows.get(0)[0] == null ? null : (Double) rows.get(0)[0];
        int reviewCount = rows.isEmpty() || rows.get(0)[1] == null ? 0 : ((Long) rows.get(0)[1]).intValue();
        long photoCount = spotPhotoRepository.countBySpotId(spotId);
        // 북마크 = 1개 이상 컬렉션에 소속
        // 북마크 쓰기(BookmarkCollectionService)가 아직 TEMP_USER_ID 기반이라 읽기도 맞춰 둔다.
        // 실제 userId로 읽으면 별이 켜지지 않는다(쓰기는 1번 사용자에게 저장되므로).
        // 북마크 인증 연동 시 아래 1L을 userId로 교체 (BookmarkCollectionService·ChecklistService와 함께).
        boolean isBookmarked = bookmarkCollectionSpotRepository.existsByCollection_UserIdAndSpotId(1L, spotId);
        Long myReviewId = userId == null ? null
                : reviewRepository.findIdsBySpotIdAndUserId(spotId, userId).stream().findFirst().orElse(null);

        return SpotDetailResponse.of(
                spot, tags, reviewTags, checklist,
                avgRating != null ? Math.round(avgRating * 10) / 10.0 : 0.0,
                reviewCount, photoCount, isBookmarked, myReviewId
        );
    }

    public List<RecommendedSpotResponse> getRecommendedSpots(int limit) {
        return spotRepository.findRecommendedSpots(Math.min(limit, 20))
                .stream()
                .map(RecommendedSpotResponse::from)
                .toList();
    }

    public List<NearbySpotResponse> getNearbySpots(Double lat, Double lng, Double radiusKm, int limit) {
        List<Spot> spots = spotRepository.findNearbySpots(lat, lng, radiusKm, Math.min(limit, 50));
        return spots.stream()
                .map(spot -> {
                    double distance = calcDistance(lat, lng, spot.getLatitude(), spot.getLongitude());
                    return NearbySpotResponse.of(spot, distance);
                })
                .toList();
    }

    private double calcDistance(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    public SpotPhotoResponse getSpotPhotos(Long spotId) {
        if (!spotRepository.existsById(spotId)) {
            throw new CustomException(SpotErrorCode.SPOT_NOT_FOUND);
        }
        // sync 때 저장한 TourAPI 사진을 DB에서 조회 (실시간 외부 호출 제거)
        return SpotPhotoResponse.of(spotId, spotPhotoRepository.findBySpotIdAndUserIdIsNullOrderByIdAsc(spotId));
    }

    public Page<SpotResponse> getSpots(String category, String sort, int page, int size) {
        SpotCategory spotCategory = parseCategory(category);
        Pageable pageable = createPageable(page, size, sort);

        if (spotCategory == null) {
            return spotRepository.findAllByStatusAndIsActiveTrue(
                    SpotStatus.APPROVED,
                    pageable
            ).map(SpotResponse::from);
        }

        return spotRepository.findAllByCategoryAndStatusAndIsActiveTrue(
                spotCategory,
                SpotStatus.APPROVED,
                pageable
        ).map(SpotResponse::from);
    }

    // 북마크 수와 리뷰 수를 기준으로 인기스팟 조회
    public List<SpotResponse> getPopularSpots(String category, int size){
        SpotCategory spotCategory = parseCategory(category);
        Pageable pageable = createPageable(0, size, "popular");

        if (spotCategory == null) {
            return spotRepository.findListByStatusAndIsActiveTrue(
                    SpotStatus.APPROVED,
                    pageable
            ).stream()
                    .map(SpotResponse::from)
                    .toList();
        }

        return spotRepository.findListByCategoryAndStatusAndIsActiveTrue(
                spotCategory,
                SpotStatus.APPROVED,
                pageable
        ).stream()
                .map(SpotResponse::from)
                .toList();
    }

    // 키워드로 스팟 검색하기
    public Page<SpotResponse> searchSpots(String keyword, String category, int page, int size) {
        if (keyword == null || keyword.isBlank()) {
            throw new CustomException(SpotErrorCode.SEARCH_KEYWORD_REQUIRED);
        }

        SpotCategory spotCategory = parseCategory(category);
        Pageable pageable = createPageable(page, size, "latest");

        return spotRepository.searchSpots(
                keyword.trim(),
                spotCategory,
                SpotStatus.APPROVED,
                pageable
        ).map(SpotResponse::from);
    }

    private Pageable createPageable(int page, int size, String sort) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        return PageRequest.of(safePage, safeSize, resolveSort(sort));
    }

    private Sort resolveSort(String sort) {
        if ("popular".equalsIgnoreCase(sort)) {
            return Sort.by(Sort.Direction.DESC, "bookmarkCount")
                    .and(Sort.by(Sort.Direction.DESC, "reviewCount"));
        }

        if ("score".equalsIgnoreCase(sort)) {
            return Sort.by(Sort.Direction.DESC, "photogenicScore");
        }

        return Sort.by(Sort.Direction.DESC, "createdAt");
    }

    private SpotCategory parseCategory(String category) {
        if (category == null || category.isBlank()) {
            return null;
        }

        try {
            return SpotCategory.valueOf(category.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new CustomException(SpotErrorCode.INVALID_SPOT_CATEGORY);
        }
    }

    // 지도 영역 좌표를 기준으로 화면에 표시할 스팟 핀 목록 조회
    public List<SpotMapResponse> getMapSpots(com.project.picngo.spot.dto.MapBoundsRequest request) {
        validateMapBounds(
                request.southWestLat(),
                request.southWestLng(),
                request.northEastLat(),
                request.northEastLng()
        );

        SpotCategory spotCategory = parseCategory(request.category());

        return spotRepository.findSpotsInMapBounds(
                request.southWestLat(),
                request.southWestLng(),
                request.northEastLat(),
                request.northEastLng(),
                spotCategory,
                SpotStatus.APPROVED,
                org.springframework.data.domain.PageRequest.of(0, request.getSizeOrDefault())
        ).stream()
                .map(SpotMapResponse::from)
                .toList();
    }

    private void validateMapBounds(
            Double southWestLat,
            Double southWestLng,
            Double northEastLat,
            Double northEastLng
    ){
        if (southWestLat > northEastLat || southWestLng > northEastLng) {
            throw new CustomException(SpotErrorCode.INVALID_MAP_BOUNDS);
        }
    }

    // 지도 핀을 선택했을 때 보여줄 스팟 요약 정보 조회
    public SpotSummaryResponse getSpotSummary(Long id){
        return spotRepository.findByIdAndStatusAndIsActiveTrue(id, SpotStatus.APPROVED)
                .map(SpotSummaryResponse::from)
                .orElseThrow(() -> new CustomException(SpotErrorCode.SPOT_NOT_FOUND));
    }
}
