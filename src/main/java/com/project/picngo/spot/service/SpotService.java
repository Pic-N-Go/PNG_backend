package com.project.picngo.spot.service;

import com.project.picngo.spot.domain.SpotCategory;
import com.project.picngo.spot.dto.SpotMapResponse;
import com.project.picngo.spot.dto.SpotResponse;
import com.project.picngo.spot.dto.SpotSummaryResponse;
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
            return spotRepository.findAllByIsActiveTrue(pageable)
                    .map(SpotResponse::from);
        }

        return spotRepository.findAllByCategoryAndIsActiveTrue(spotCategory, pageable)
                .map(SpotResponse::from);
    }

    public Page<SpotResponse> searchSpots(String keyword, String category, int page, int size) {
        if (keyword == null || keyword.isBlank()) {
            throw new IllegalArgumentException("검색어를 입력해주세요.");
        }

        SpotCategory spotCategory = parseCategory(category);
        Pageable pageable = createPageable(page, size, "latest");

        return spotRepository.search(keyword.trim(), spotCategory, pageable)
                .map(SpotResponse::from);
    }

    public List<SpotResponse> getPopularSpots(String category, int size) {
        SpotCategory spotCategory = parseCategory(category);
        Pageable pageable = createPageable(0, size, "popular");

        if (spotCategory == null) {
            return spotRepository.findAllByIsActiveTrue(pageable).stream()
                    .map(SpotResponse::from)
                    .toList();
        }

        return spotRepository.findAllByCategoryAndIsActiveTrue(spotCategory, pageable).stream()
                .map(SpotResponse::from)
                .toList();
    }

    public List<SpotMapResponse> getMapSpots(
            Double southWestLat,
            Double southWestLng,
            Double northEastLat,
            Double northEastLng,
            String category
    ) {
        validateMapBounds(southWestLat, southWestLng, northEastLat, northEastLng);

        SpotCategory spotCategory = parseCategory(category);
        return spotRepository.findSpotsInMapBounds(
                        southWestLat,
                        southWestLng,
                        northEastLat,
                        northEastLng,
                        spotCategory
                ).stream()
                .map(SpotMapResponse::from)
                .toList();
    }

    public SpotSummaryResponse getSpotSummary(Long id) {
        return spotRepository.findByIdAndIsActiveTrue(id)
                .map(SpotSummaryResponse::from)
                .orElseThrow(() -> new IllegalArgumentException("스팟을 찾을 수 없습니다."));
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
            throw new IllegalArgumentException("지원하지 않는 스팟 카테고리입니다.");
        }
    }

    private void validateMapBounds(
            Double southWestLat,
            Double southWestLng,
            Double northEastLat,
            Double northEastLng
    ) {
        if (southWestLat == null || southWestLng == null || northEastLat == null || northEastLng == null) {
            throw new IllegalArgumentException("지도 영역 좌표를 모두 입력해주세요.");
        }

        if (southWestLat > northEastLat || southWestLng > northEastLng) {
            throw new IllegalArgumentException("지도 영역 좌표가 올바르지 않습니다.");
        }
    }
}
