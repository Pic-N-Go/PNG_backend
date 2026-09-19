package com.project.picngo.spot.service;

import com.project.picngo.common.domain.SpotCategory;
import com.project.picngo.external.AccessibilityTourApiClient;
import com.project.picngo.external.dto.AccessibilityTourDetailResponse;
import com.project.picngo.external.dto.AccessibilityTourSyncListResponse;
import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.dto.AccessibilityTourSyncResultResponse;
import com.project.picngo.spot.repository.SpotAccessibilityInfoRepository;
import com.project.picngo.spot.repository.SpotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AccessibilityTourSyncService {

    private static final int PAGE_SIZE = 500;

    private final AccessibilityTourApiClient apiClient;
    private final SpotRepository spotRepository;
    private final SpotAccessibilityInfoRepository accessibilityInfoRepository;
    private final SpotAccessibilityInfoUpsertService upsertService;

    public AccessibilityTourSyncResultResponse syncMatchedSpots(int contentTypeId, int maxDetails) {
        return syncMatchedSpots(contentTypeId, maxDetails, null);
    }

    public AccessibilityTourSyncResultResponse syncMatchedSpots(int contentTypeId, int maxDetails, Integer areaCode) {
        if (maxDetails < 1) {
            throw new IllegalArgumentException("maxDetails는 1 이상이어야 합니다.");
        }

        int pageNo = 1;
        int totalCount = 0;
        int matched = 0;
        int requested = 0;
        int saved = 0;
        int noDetail = 0;
        int failed = 0;

        do {
            AccessibilityTourSyncListResponse response =
                    apiClient.getSyncList(contentTypeId, pageNo, PAGE_SIZE, areaCode);
            var body = response.response().body();
            if (body == null) {
                break;
            }

            totalCount = body.totalCount();
            List<String> contentIds = body.items() == null
                    ? List.of()
                    : body.items().safeItems().stream()
                    .filter(item -> "1".equals(item.showflag()))
                    .map(AccessibilityTourSyncListResponse.Item::contentid)
                    .filter(id -> id != null && !id.isBlank())
                    .distinct()
                    .toList();
            List<Spot> spots = contentIds.isEmpty()
                    ? List.of()
                    : spotRepository.findTourApiAddonTargets(
                            contentIds,
                            contentTypeId,
                            SpotCategory.CAFE
                    );
            matched += spots.size();

            List<Long> spotIds = spots.stream().map(Spot::getId).toList();
            Set<Long> existing = spotIds.isEmpty()
                    ? Set.of()
                    : accessibilityInfoRepository.findExistingSpotIds(spotIds);
            Map<String, Spot> byContentId = spots.stream().collect(Collectors.toMap(
                    Spot::getTourContentId,
                    Function.identity(),
                    (first, ignored) -> first
            ));

            for (String contentId : contentIds) {
                if (requested >= maxDetails) {
                    return result(contentTypeId, matched, requested, saved, noDetail, failed);
                }
                Spot spot = byContentId.get(contentId);
                if (spot == null || existing.contains(spot.getId())) {
                    continue;
                }

                requested++;
                AccessibilityTourDetailResponse.Item detail = apiClient.getDetail(contentId);
                if (detail == null) {
                    noDetail++;
                } else {
                    upsertService.upsert(spot, detail);
                    saved++;
                }
                sleep(150);
            }
            pageNo++;
        } while ((pageNo - 1) * PAGE_SIZE < totalCount);

        return result(contentTypeId, matched, requested, saved, noDetail, failed);
    }

    private AccessibilityTourSyncResultResponse result(
            int type,
            int matched,
            int requested,
            int saved,
            int noDetail,
            int failed
    ) {
        return new AccessibilityTourSyncResultResponse(
                type,
                matched,
                requested,
                saved,
                noDetail,
                failed
        );
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("무장애 동기화가 중단되었습니다.", e);
        }
    }
}
