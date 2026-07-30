package com.project.picngo.spot.service;

import com.project.picngo.bookmark.repository.BookmarkCollectionSpotRepository;
import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.SpotErrorCode;
import com.project.picngo.spot.domain.ChecklistMapper;
import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.domain.SpotTag;
import com.project.picngo.common.domain.SpotCategory;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SpotService {

    // ponytail: Spring Security 연동 전까지 하드코딩
    private static final Long TEMP_USER_ID = 1L;
    private static final int MAX_PAGE_SIZE = 50;

    private final SpotRepository spotRepository;
    private final SpotTagRepository spotTagRepository;
    private final ReviewRepository reviewRepository;
    private final SpotPhotoRepository spotPhotoRepository;
    private final BookmarkCollectionSpotRepository bookmarkCollectionSpotRepository;

    public SpotDetailResponse getSpotDetail(Long spotId) {
        Spot spot = spotRepository.findById(spotId)
                .orElseThrow(() -> new CustomException(SpotErrorCode.SPOT_NOT_FOUND));

        List<SpotTag> tags = spotTagRepository.findBySpotId(spotId);
        List<String> checklist = ChecklistMapper.getChecklist(spot.getCat3());

        List<Object[]> rows = reviewRepository.findAvgAndCountBySpotId(spotId);
        Double avgRating = rows.isEmpty() || rows.get(0)[0] == null ? null : (Double) rows.get(0)[0];
        int reviewCount = rows.isEmpty() || rows.get(0)[1] == null ? 0 : ((Long) rows.get(0)[1]).intValue();
        long photoCount = spotPhotoRepository.countBySpotId(spotId);
        // 북마크 = 1개 이상 컬렉션에 소속
        boolean isBookmarked = bookmarkCollectionSpotRepository.existsByCollection_UserIdAndSpotId(TEMP_USER_ID, spotId);

        return SpotDetailResponse.of(
                spot, tags, checklist,
                avgRating != null ? Math.round(avgRating * 10) / 10.0 : 0.0,
                reviewCount, photoCount, isBookmarked
        );
    }

    public List<RecommendedSpotResponse> getRecommendedSpots(Long userId, int limit) {
        return spotRepository.findRecommendedSpots(userId, Math.min(limit, 20))
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

    public Page<SpotResponse> getSpots(List<String> category, String sort, int page, int size) {
        List<SpotCategory> spotCategories = parseCategories(category);
        Pageable pageable = createPageable(page, size, sort);

        if (spotCategories == null) {
            return spotRepository.findAllByStatusAndIsActiveTrue(
                    SpotStatus.APPROVED,
                    pageable
            ).map(SpotResponse::from);
        }

        return spotRepository.findAllByCategoriesAndStatusAndIsActiveTrue(
                spotCategories,
                SpotStatus.APPROVED,
                pageable
        ).map(SpotResponse::from);
    }

    // 북마크 수와 리뷰 수를 기준으로 인기스팟 조회
    public List<SpotResponse> getPopularSpots(List<String> category, int size){
        List<SpotCategory> spotCategories = parseCategories(category);
        Pageable pageable = createPageable(0, size, "popular");

        if (spotCategories == null) {
            return spotRepository.findListByStatusAndIsActiveTrue(
                    SpotStatus.APPROVED,
                    pageable
            ).stream()
                    .map(SpotResponse::from)
                    .toList();
        }

        return spotRepository.findListByCategoriesAndStatusAndIsActiveTrue(
                spotCategories,
                SpotStatus.APPROVED,
                pageable
        ).stream()
                .map(SpotResponse::from)
                .toList();
    }

    // 키워드로 스팟 검색하기
    public Page<SpotResponse> searchSpots(String keyword, List<String> category, int page, int size) {
        if (keyword == null || keyword.isBlank()) {
            throw new CustomException(SpotErrorCode.SEARCH_KEYWORD_REQUIRED);
        }

        List<SpotCategory> spotCategories = parseCategories(category);
        Pageable pageable = createPageable(page, size, "latest");

        if (spotCategories == null) {
            return spotRepository.searchSpots(
                    keyword.trim(),
                    SpotStatus.APPROVED,
                    pageable
            ).map(SpotResponse::from);
        }

        return spotRepository.searchSpotsByCategories(
                keyword.trim(),
                spotCategories,
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

    // 카테고리 다중 선택. 값이 없으면 null을 돌려주고 호출부가 필터 없는 쿼리로 분기한다.
    // (컬렉션 파라미터에 null/빈 리스트를 넘기면 IN 절 렌더링이 깨지므로 빈 리스트를 만들지 않는다.)
    private List<SpotCategory> parseCategories(List<String> categories) {
        if (categories == null || categories.isEmpty()) {
            return null;
        }

        List<SpotCategory> parsed = categories.stream()
                .filter(c -> c != null && !c.isBlank())
                .flatMap(c -> java.util.Arrays.stream(c.split(",")))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(this::parseCategoryOrNull)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();

        return parsed.isEmpty() ? null : parsed;
    }

    private SpotCategory parseCategoryOrNull(String category) {
        try {
            return SpotCategory.valueOf(category.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.debug("미지원/유효하지 않은 스팟 카테고리 요청 파라미터 스킵: {}", category);
            return null;
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

        List<SpotCategory> spotCategories = parseCategories(request.category());
        Pageable mapPageable = PageRequest.of(0, request.getSizeOrDefault());

        List<Spot> spots = (spotCategories == null)
                ? spotRepository.findSpotsInMapBounds(
                        request.southWestLat(),
                        request.southWestLng(),
                        request.northEastLat(),
                        request.northEastLng(),
                        SpotStatus.APPROVED,
                        mapPageable)
                : spotRepository.findSpotsInMapBoundsByCategories(
                        request.southWestLat(),
                        request.southWestLng(),
                        request.northEastLat(),
                        request.northEastLng(),
                        spotCategories,
                        SpotStatus.APPROVED,
                        mapPageable);

        return spots.stream()
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
