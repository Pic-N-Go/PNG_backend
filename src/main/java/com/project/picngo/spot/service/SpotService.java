package com.project.picngo.spot.service;

import com.project.picngo.bookmark.repository.BookmarkCollectionSpotRepository;
import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.SpotErrorCode;
import com.project.picngo.spot.domain.ChecklistMapper;
import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.domain.SpotTag;
import com.project.picngo.spot.domain.enums.ReviewTag;
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
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
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

    private static final int MAX_PAGE_SIZE = 50;

    // 검색 계측 지표 이름. 버킷/SLO 설정은 application.yaml의 management.metrics.distribution에 있다.
    private static final String SEARCH_TIMER = "spot.search.duration";
    private static final String SEARCH_RESULT_COUNTER = "spot.search.result";
    private static final String TYPE_KEYWORD = "keyword";
    private static final String TYPE_MAP = "map";
    private static final String PHASE_QUERY = "query";
    private static final String PHASE_MAPPING = "mapping";

    private final SpotRepository spotRepository;
    private final SpotTagRepository spotTagRepository;
    private final ReviewRepository reviewRepository;
    private final SpotPhotoRepository spotPhotoRepository;
    private final BookmarkCollectionSpotRepository bookmarkCollectionSpotRepository;
    private final MeterRegistry meterRegistry;

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
        // 북마크 = 1개 이상 컬렉션에 소속. userId가 null(익명 조회)이면 항상 false.
        boolean isBookmarked = userId != null
                && bookmarkCollectionSpotRepository.existsByCollection_UserIdAndSpotId(userId, spotId);
        Long myReviewId = userId == null ? null
                : reviewRepository.findIdsBySpotIdAndUserId(spotId, userId).stream().findFirst().orElse(null);

        return SpotDetailResponse.of(
                spot, tags, reviewTags, checklist,
                avgRating != null ? Math.round(avgRating * 10) / 10.0 : 0.0,
                reviewCount, photoCount, isBookmarked, myReviewId
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
        boolean filtered = (spotCategories != null);

        Timer.Sample querySample = Timer.start(meterRegistry);
        Page<Spot> spots = filtered
                ? spotRepository.searchSpotsByCategories(
                        keyword.trim(),
                        spotCategories,
                        SpotStatus.APPROVED,
                        pageable)
                : spotRepository.searchSpots(
                        keyword.trim(),
                        SpotStatus.APPROVED,
                        pageable);
        querySample.stop(searchTimer(TYPE_KEYWORD, PHASE_QUERY, filtered));

        recordSearchOutcome(TYPE_KEYWORD, spots.getTotalElements());

        Timer.Sample mappingSample = Timer.start(meterRegistry);
        Page<SpotResponse> response = spots.map(SpotResponse::from);
        mappingSample.stop(searchTimer(TYPE_KEYWORD, PHASE_MAPPING, filtered));

        return response;
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
        boolean filtered = (spotCategories != null);

        Timer.Sample querySample = Timer.start(meterRegistry);
        List<Spot> spots = filtered
                ? spotRepository.findSpotsInMapBoundsByCategories(
                        request.southWestLat(),
                        request.southWestLng(),
                        request.northEastLat(),
                        request.northEastLng(),
                        spotCategories,
                        SpotStatus.APPROVED,
                        mapPageable)
                : spotRepository.findSpotsInMapBounds(
                        request.southWestLat(),
                        request.southWestLng(),
                        request.northEastLat(),
                        request.northEastLng(),
                        SpotStatus.APPROVED,
                        mapPageable);
        querySample.stop(searchTimer(TYPE_MAP, PHASE_QUERY, filtered));

        recordSearchOutcome(TYPE_MAP, spots.size());

        Timer.Sample mappingSample = Timer.start(meterRegistry);
        List<SpotMapResponse> response = spots.stream()
                .map(SpotMapResponse::from)
                .toList();
        mappingSample.stop(searchTimer(TYPE_MAP, PHASE_MAPPING, filtered));

        return response;
    }

    // 검색 지연을 쿼리 구간과 DTO 매핑 구간으로 쪼개서 잰다.
    // 매핑 구간에도 DB 시간이 섞이는 게 정상이다 - Spot.categories가 LAZY라
    // SpotResponse.from()의 getCategoryNames()에서 스팟마다 추가 select가 나간다.
    // 두 구간을 나눠 재야 "검색 SQL 자체가 느린 것"과 "N+1이 느린 것"을 구분할 수 있고,
    // 이 구분이 없으면 어떤 개선(인덱스 / fetch join / 스토리지 교체)이 유효한지 알 수 없다.
    //
    // filtered 태그를 붙이는 이유: 카테고리 필터가 붙으면 컬렉션 상관 서브쿼리가
    // 추가돼서 실행 계획이 완전히 달라진다. 한 계열로 섞으면 두 분포가 겹쳐 보인다.
    // 태그는 전부 저카디널리티 값만 쓴다(검색어를 태그로 넣으면 시계열이 폭발한다).
    private Timer searchTimer(String type, String phase, boolean filtered) {
        return Timer.builder(SEARCH_TIMER)
                .description("스팟 검색 처리 시간")
                .tag("type", type)
                .tag("phase", phase)
                .tag("filtered", String.valueOf(filtered))
                .register(meterRegistry);
    }

    // 결과가 0건인 검색의 비율.
    // LIKE '%키워드%' 기반 검색은 오타/동의어/의미 검색을 원리적으로 못 잡는데,
    // 이건 인덱스를 붙여도 해결되지 않는 한계라 응답 속도와는 별개로 추적해야 한다.
    // "검색 요청의 N%가 0건 반환"이 검색 방식 자체를 바꿀지 판단하는 근거가 된다.
    private void recordSearchOutcome(String type, long resultCount) {
        Counter.builder(SEARCH_RESULT_COUNTER)
                .description("스팟 검색 결과 유무")
                .tag("type", type)
                .tag("outcome", resultCount == 0 ? "zero" : "hit")
                .register(meterRegistry)
                .increment();
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
