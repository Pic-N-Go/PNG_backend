package com.project.picngo.spot.service;

import com.project.picngo.external.PetTourApiClient;
import com.project.picngo.external.dto.PetTourSyncListResponse;
import com.project.picngo.external.dto.PetTourDetailResponse;
import com.project.picngo.spot.domain.Spot;
import com.project.picngo.common.domain.SpotCategory;
import com.project.picngo.spot.dto.PetTourMatchStatusResponse;
import com.project.picngo.spot.dto.PetTourSyncResultResponse;
import com.project.picngo.spot.repository.SpotRepository;
import com.project.picngo.spot.repository.SpotPetInfoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PetTourSyncService {

    /**
     * 첫 페이지 응답의 totalCount로 남은 페이지 수를 판단한다.
     * 관광공사 목록 응답이 1,000건에서도 256KB를 넘으므로 500건씩 조회한다.
     */
    private static final int PAGE_SIZE = 500;

    private final PetTourApiClient petTourApiClient;
    private final SpotRepository spotRepository;
    private final SpotPetInfoRepository spotPetInfoRepository;
    private final SpotPetInfoUpsertService spotPetInfoUpsertService;

    /**
     * 반려동물 목록과 현재 Spot DB의 교집합만 계산한다.
     * 상세 API 호출이나 DB 변경은 하지 않는다.
     */
    public PetTourMatchStatusResponse getMatchStatus(int contentTypeId) {
        int pageNo = 1;
        int totalCount = 0;
        int receivedCount = 0;
        int matchedSpotCount = 0;

        do {
            PetTourSyncListResponse response =
                    petTourApiClient.getSyncList(contentTypeId, pageNo, PAGE_SIZE);
            var body = response.response().body();
            if (body == null) {
                break;
            }

            totalCount = body.totalCount();
            List<PetTourSyncListResponse.Item> items = body.items() == null
                    ? List.of()
                    : body.items().safeItems();
            receivedCount += items.size();

            List<String> contentIds = items.stream()
                    .filter(item -> "1".equals(item.showflag()))
                    .map(PetTourSyncListResponse.Item::contentid)
                    .filter(id -> id != null && !id.isBlank())
                    .distinct()
                    .toList();

            List<Spot> matchedSpots = findMatchedSpots(contentIds, contentTypeId);
            matchedSpotCount += matchedSpots.size();
            pageNo++;
        } while ((pageNo - 1) * PAGE_SIZE < totalCount);

        return new PetTourMatchStatusResponse(
                contentTypeId,
                totalCount,
                receivedCount,
                matchedSpotCount
        );
    }

    public PetTourSyncResultResponse syncMatchedSpots(int contentTypeId, int maxDetails) {
        return syncMatchedSpots(contentTypeId, maxDetails, null);
    }

    public PetTourSyncResultResponse syncMatchedSpots(int contentTypeId, int maxDetails, Integer legalRegionCode) {
        if (maxDetails < 1) {
            throw new IllegalArgumentException("maxDetails는 1 이상이어야 합니다.");
        }

        int pageNo = 1;
        int totalCount = 0;
        int matchedSpotCount = 0;
        int requestedDetailCount = 0;
        int savedCount = 0;
        int noDetailCount = 0;
        int failedCount = 0;

        do {
            PetTourSyncListResponse response =
                    petTourApiClient.getSyncList(contentTypeId, pageNo, PAGE_SIZE, legalRegionCode);
            var body = response.response().body();
            if (body == null) break;

            totalCount = body.totalCount();
            List<String> contentIds = body.items() == null
                    ? List.of()
                    : body.items().safeItems().stream()
                    .filter(item -> "1".equals(item.showflag()))
                    .map(PetTourSyncListResponse.Item::contentid)
                    .filter(id -> id != null && !id.isBlank())
                    .distinct()
                    .toList();

            List<Spot> matchedSpots = findMatchedSpots(contentIds, contentTypeId);
            matchedSpotCount += matchedSpots.size();

            List<Long> matchedSpotIds = matchedSpots.stream()
                    .map(Spot::getId)
                    .toList();
            Set<Long> alreadySyncedSpotIds = matchedSpotIds.isEmpty()
                    ? Set.of()
                    : spotPetInfoRepository.findExistingSpotIds(matchedSpotIds);

            Map<String, Spot> spotByContentId = matchedSpots.stream()
                    .collect(Collectors.toMap(
                            Spot::getTourContentId,
                            Function.identity(),
                            (first, ignored) -> first
                    ));

            for (String contentId : contentIds) {
                if (requestedDetailCount >= maxDetails) {
                    return result(contentTypeId, matchedSpotCount, requestedDetailCount,
                            savedCount, noDetailCount, failedCount);
                }
                Spot spot = spotByContentId.get(contentId);
                if (spot == null) continue;
                if (alreadySyncedSpotIds.contains(spot.getId())) continue;

                requestedDetailCount++;
                PetTourDetailResponse.Item detail = petTourApiClient.getDetail(contentId);
                if (detail == null) {
                    noDetailCount++;
                } else {
                    spotPetInfoUpsertService.upsert(spot, detail);
                    savedCount++;
                }
                sleep(150);
            }
            pageNo++;
        } while ((pageNo - 1) * PAGE_SIZE < totalCount);

        return result(contentTypeId, matchedSpotCount, requestedDetailCount,
                savedCount, noDetailCount, failedCount);
    }

    private PetTourSyncResultResponse result(
            int contentTypeId,
            int matchedSpotCount,
            int requestedDetailCount,
            int savedCount,
            int noDetailCount,
            int failedCount
    ) {
        return new PetTourSyncResultResponse(
                contentTypeId,
                matchedSpotCount,
                requestedDetailCount,
                savedCount,
                noDetailCount,
                failedCount
        );
    }

    private List<Spot> findMatchedSpots(List<String> contentIds, int contentTypeId) {
        return contentIds.isEmpty()
                ? List.of()
                : spotRepository.findTourApiAddonTargets(contentIds, contentTypeId, SpotCategory.CAFE);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("반려동물 동기화가 중단되었습니다.", e);
        }
    }
}
