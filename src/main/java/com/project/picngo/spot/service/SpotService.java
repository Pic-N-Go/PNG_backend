package com.project.picngo.spot.service;

import com.project.picngo.spot.domain.SpotCategory;
import com.project.picngo.spot.domain.SpotStatus;
import com.project.picngo.spot.dto.SpotResponse;
import com.project.picngo.spot.repository.SpotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}
