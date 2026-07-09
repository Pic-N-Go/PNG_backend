package com.project.picngo.spot.service;

import com.project.picngo.spot.domain.SpotCategory;
import com.project.picngo.spot.domain.SpotStatus;
import com.project.picngo.spot.dto.SpotMapResponse;
import com.project.picngo.spot.dto.SpotResponse;
import com.project.picngo.spot.repository.SpotRepository;
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
            return spotRepository.findAllByStatusAndIsActiveTrue(
                    SpotStatus.APPROVED,
                    pageable
            ).stream()
                    .map(SpotResponse::from)
                    .toList();
        }

        return spotRepository.findAllByCategoryAndStatusAndIsActiveTrue(
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
            throw new IllegalArgumentException("검색어를 입력해주세요.");
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
            throw new IllegalArgumentException("吏?먰븯吏 ?딅뒗 ?ㅽ뙚 移댄뀒怨좊━?낅땲??");
        }
    }

    // 지도 영역 좌표를 기준으로 화면에 표시할 스팟 핀 목록 조회
    public List<SpotMapResponse> getMapSpots(
            Double southWestLat,
            Double southWestLng,
            Double northEastLat,
            Double northEastLng,
            String category
    ){
        validateMapBounds(southWestLat, southWestLng, northEastLat, northEastLng);

        SpotCategory spotCategory = parseCategory(category);

        return spotRepository.findSpotsInMapBounds(
                southWestLat,
                southWestLng,
                northEastLat,
                northEastLng,
                spotCategory,
                SpotStatus.APPROVED
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
        if (southWestLat == null || southWestLng == null || northEastLat == null || northEastLng == null) {
            throw new IllegalArgumentException("지도 영역 좌표를 모두 입력해주세요.");
        }

        if (southWestLat > northEastLat || southWestLng > northEastLng) {
            throw new IllegalArgumentException("지도 영역 좌표가 올바르지 않습니다.");
        }
    }
}
