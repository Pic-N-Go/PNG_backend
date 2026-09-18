package com.project.picngo.spot.service;

import com.project.picngo.external.KakaoLocalSearchClient;
import com.project.picngo.external.LegalDongMapper;
import com.project.picngo.external.PhotoAwardApiClient;
import com.project.picngo.external.dto.PhotoAwardApiResponse;
import com.project.picngo.external.dto.PhotoAwardApiResponse.PhotoAwardItem;
import com.project.picngo.spot.dto.Coordinate;
import com.project.picngo.spot.repository.PhotoAwardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class PhotoAwardSyncService {

    private static final int PAGE_SIZE = 50;
    private static final long API_CALL_DELAY_MS = 150;

    private final PhotoAwardApiClient photoAwardApiClient;
    private final KakaoLocalSearchClient kakaoLocalSearchClient;
    private final PhotoAwardRepository photoAwardRepository;
    private final PhotoAwardUpsertService photoAwardUpsertService;
    private final PhotoAwardSyncStatusManager syncStatusManager;

    private enum QueryStrategy {
        BY_LDONG_CODE,
        BY_SYNC_LIST,
        BY_KEYWORD,
        BY_ALL_FILTER
    }

    /**
     * 특정 지역(법정동 시도 코드) 공모전 수상작 동기화
     */
    public int sync(Integer lDongRegnCd) {
        String regionName = LegalDongMapper.getRegionName(lDongRegnCd);
        String shortName = regionName.length() >= 2 ? regionName.substring(0, 2) : regionName;
        log.info("[PhotoAwardSyncService] 지역({}) 공모전 동기화 시작 (lDongRegnCd={}, shortName={})",
                regionName, lDongRegnCd, shortName);

        int pageNo = 1;
        int saved = 0;
        int created = 0;
        int enriched = 0;
        int skipped = 0;
        int failedGeocoding = 0;
        int totalCount = Integer.MAX_VALUE;

        // 1단계: 어떤 전략으로 조회할지 1페이지 탐색 (4단계 폴백)
        QueryStrategy strategy = QueryStrategy.BY_LDONG_CODE;
        PhotoAwardApiResponse firstResp = photoAwardApiClient.getPhotoAwardList(lDongRegnCd, null, 1, PAGE_SIZE);

        if (isEmptyResponse(firstResp)) {
            log.info("[PhotoAwardSyncService] 지역({}) 1순위 phokoAwrdList(lDongRegnCd={}) 데이터 없음 -> 2순위 phokoAwrdSyncList 시도",
                    regionName, lDongRegnCd);
            firstResp = photoAwardApiClient.getPhotoAwardSyncList(lDongRegnCd, 1, PAGE_SIZE);
            if (!isEmptyResponse(firstResp)) {
                strategy = QueryStrategy.BY_SYNC_LIST;
                log.info("[PhotoAwardSyncService] 지역({}) 2순위 phokoAwrdSyncList 채택: totalCount={}",
                        regionName, firstResp.response().body().totalCount());
            }
        }

        if (isEmptyResponse(firstResp)) {
            log.info("[PhotoAwardSyncService] 지역({}) 2순위 syncList 데이터 없음 -> 3순위 키워드('{}') 검색 시도",
                    regionName, shortName);
            firstResp = photoAwardApiClient.getPhotoAwardList(null, shortName, 1, PAGE_SIZE);
            if (!isEmptyResponse(firstResp)) {
                strategy = QueryStrategy.BY_KEYWORD;
                log.info("[PhotoAwardSyncService] 지역({}) 3순위 키워드 검색 채택: totalCount={}",
                        regionName, firstResp.response().body().totalCount());
            }
        }

        if (isEmptyResponse(firstResp)) {
            log.info("[PhotoAwardSyncService] 지역({}) 3순위 키워드 검색 데이터 없음 -> 4순위 전체 목록 조회 후 메모리 매칭 채택",
                    regionName);
            firstResp = photoAwardApiClient.getPhotoAwardList(null, null, 1, PAGE_SIZE);
            if (!isEmptyResponse(firstResp)) {
                strategy = QueryStrategy.BY_ALL_FILTER;
                log.info("[PhotoAwardSyncService] 지역({}) 4순위 전체 목록 조회 채택: totalCount={}",
                        regionName, firstResp.response().body().totalCount());
            }
        }

        if (isEmptyResponse(firstResp)) {
            log.warn("[PhotoAwardSyncService] 지역({}) 모든 전략으로도 데이터를 수집할 수 없습니다 (API에 해당 데이터가 존재하지 않음)", regionName);
            return 0;
        }

        totalCount = firstResp.response().body().totalCount();
        log.info("[PhotoAwardSyncService] 지역({}) 최종 동기화 전략: {}, 총 대상 건수: {}", regionName, strategy, totalCount);

        while ((pageNo - 1) * PAGE_SIZE < totalCount) {
            PhotoAwardApiResponse response;
            if (pageNo == 1) {
                response = firstResp;
            } else {
                response = switch (strategy) {
                    case BY_LDONG_CODE -> photoAwardApiClient.getPhotoAwardList(lDongRegnCd, null, pageNo, PAGE_SIZE);
                    case BY_SYNC_LIST -> photoAwardApiClient.getPhotoAwardSyncList(lDongRegnCd, pageNo, PAGE_SIZE);
                    case BY_KEYWORD -> photoAwardApiClient.getPhotoAwardList(null, shortName, pageNo, PAGE_SIZE);
                    case BY_ALL_FILTER -> photoAwardApiClient.getPhotoAwardList(null, null, pageNo, PAGE_SIZE);
                };
            }

            if (isEmptyResponse(response)) {
                log.info("[PhotoAwardSyncService] 지역({}) 페이지 종료: page={}", regionName, pageNo);
                break;
            }

            totalCount = response.response().body().totalCount();
            List<PhotoAwardItem> rawItems = response.response().body().items().item();
            if (rawItems == null || rawItems.isEmpty()) {
                break;
            }

            // BY_ALL_FILTER 전략인 경우 해당 지역과 연관된 항목만 필터링
            List<PhotoAwardItem> items;
            if (strategy == QueryStrategy.BY_ALL_FILTER) {
                items = rawItems.stream()
                        .filter(item -> isMatchRegion(item, lDongRegnCd, shortName))
                        .toList();
                log.info("[PhotoAwardSyncService] 전체 목록(page={}) {}건 중 지역({}) 매칭: {}건",
                        pageNo, rawItems.size(), regionName, items.size());
            } else {
                items = rawItems;
            }

            // 중복 방지를 위한 기존 contentId 배치 조회
            List<String> contentIds = items.stream()
                    .map(PhotoAwardItem::contentId)
                    .filter(id -> id != null && !id.isBlank())
                    .toList();
            Set<String> existingIds = contentIds.isEmpty()
                    ? Collections.emptySet()
                    : photoAwardRepository.findExistingContentIds(contentIds);

            for (PhotoAwardItem item : items) {
                if (item.contentId() != null && existingIds.contains(item.contentId())) {
                    skipped++;
                    continue;
                }

                sleep(API_CALL_DELAY_MS);

                // 촬영지명(koFilmst) 카카오 지오코딩
                Optional<Coordinate> coordOpt = geocode(item.koFilmst());
                if (coordOpt.isEmpty()) {
                    failedGeocoding++;
                    log.warn("[PhotoAwardSyncService] 지오코딩 실패로 건너뜀: contentId={}, title={}, koFilmst={}",
                            item.contentId(), item.koTitle(), item.koFilmst());
                    continue;
                }

                Coordinate coord = coordOpt.get();
                PhotoAwardUpsertService.UpsertResult result = photoAwardUpsertService.upsertAward(item, coord);

                switch (result) {
                    case CREATED -> {
                        created++;
                        saved++;
                    }
                    case ENRICHED -> {
                        enriched++;
                        saved++;
                    }
                    case SKIPPED -> skipped++;
                }

                if (syncStatusManager != null) {
                    syncStatusManager.updateProgress(
                            saved,
                            created,
                            enriched,
                            totalCount,
                            String.format("공모전 동기화 진행 중 (%s) - 처리 %d건 (신규 %d건, 보강 %d건, 건너뜀 %d건) / 총 %d건",
                                    regionName, saved, created, enriched, skipped, totalCount)
                    );
                }
            }

            log.info("[PhotoAwardSyncService] 지역({}) page={}/{} 완료 (누적 신규 {}건, 보강 {}건, 건너뜀 {}건, 지오코딩 실패 {}건)",
                    regionName, pageNo, (int) Math.ceil((double) totalCount / PAGE_SIZE),
                    created, enriched, skipped, failedGeocoding);
            pageNo++;
        }

        log.info("[PhotoAwardSyncService] 지역({}) 공모전 동기화 최종 완료: 총 {}건 처리 (신규 {}건, 기존 보강 {}건, 건너뜀 {}건, 지오코딩 실패 {}건)",
                regionName, saved, created, enriched, skipped, failedGeocoding);
        return saved;
    }

    /**
     * 전국 17개 지역 전체 공모전 수상작 일괄 동기화
     */
    public int syncAll() {
        log.info("[PhotoAwardSyncService] 전국 공모전 수상작 전체 일괄 동기화 시작");
        int pageNo = 1;
        int saved = 0;
        int created = 0;
        int enriched = 0;
        int skipped = 0;
        int failedGeocoding = 0;
        int totalCount = Integer.MAX_VALUE;

        while ((pageNo - 1) * PAGE_SIZE < totalCount) {
            PhotoAwardApiResponse response = photoAwardApiClient.getPhotoAwardList(null, null, pageNo, PAGE_SIZE);
            if (isEmptyResponse(response)) {
                if (pageNo == 1) {
                    log.info("[PhotoAwardSyncService] 전국 phokoAwrdList 결과 없음 -> phokoAwrdSyncList 시도");
                    response = photoAwardApiClient.getPhotoAwardSyncList(null, 1, PAGE_SIZE);
                }
                if (isEmptyResponse(response)) {
                    log.info("[PhotoAwardSyncService] 전국 동기화 완료 또는 데이터 없음: page={}", pageNo);
                    break;
                }
            }

            totalCount = response.response().body().totalCount();
            List<PhotoAwardItem> items = response.response().body().items().item();
            if (items == null || items.isEmpty()) {
                break;
            }

            log.info("[PhotoAwardSyncService] 전국 동기화 API 수신: page={}/{}, 항목 수={}건, 전체 대상={}건",
                    pageNo, (int) Math.ceil((double) totalCount / PAGE_SIZE), items.size(), totalCount);

            List<String> contentIds = items.stream()
                    .map(PhotoAwardItem::contentId)
                    .filter(id -> id != null && !id.isBlank())
                    .toList();
            Set<String> existingIds = contentIds.isEmpty()
                    ? Collections.emptySet()
                    : photoAwardRepository.findExistingContentIds(contentIds);

            for (PhotoAwardItem item : items) {
                if (item.contentId() != null && existingIds.contains(item.contentId())) {
                    skipped++;
                    continue;
                }

                sleep(API_CALL_DELAY_MS);

                Optional<Coordinate> coordOpt = geocode(item.koFilmst());
                if (coordOpt.isEmpty()) {
                    failedGeocoding++;
                    log.warn("[PhotoAwardSyncService] 지오코딩 실패로 건너뜀: contentId={}, title={}, koFilmst={}",
                            item.contentId(), item.koTitle(), item.koFilmst());
                    continue;
                }

                Coordinate coord = coordOpt.get();
                PhotoAwardUpsertService.UpsertResult result = photoAwardUpsertService.upsertAward(item, coord);

                switch (result) {
                    case CREATED -> {
                        created++;
                        saved++;
                    }
                    case ENRICHED -> {
                        enriched++;
                        saved++;
                    }
                    case SKIPPED -> skipped++;
                }

                if (syncStatusManager != null) {
                    syncStatusManager.updateProgress(
                            saved,
                            created,
                            enriched,
                            totalCount,
                            String.format("전국 공모전 동기화 진행 중 (page %d/%d) - 처리 %d건 (신규 %d건, 보강 %d건) / 총 %d건",
                                    pageNo, (int) Math.ceil((double) totalCount / PAGE_SIZE),
                                    saved, created, enriched, totalCount)
                    );
                }
            }

            pageNo++;
        }

        log.info("[PhotoAwardSyncService] 전국 공모전 수상작 동기화 최종 완료: 총 {}건 저장 (신규 {}건, 보강 {}건, 건너뜀 {}건, 지오코딩 실패 {}건)",
                saved, created, enriched, skipped, failedGeocoding);
        return saved;
    }

    private boolean isMatchRegion(PhotoAwardItem item, Integer lDongRegnCd, String shortName) {
        if (item == null) return false;
        if (lDongRegnCd != null && item.lDongRegnCd() != null) {
            try {
                if (Integer.parseInt(item.lDongRegnCd().trim()) == lDongRegnCd) {
                    return true;
                }
            } catch (NumberFormatException ignored) {}
        }
        if (item.koFilmst() != null && item.koFilmst().contains(shortName)) {
            return true;
        }
        if (item.koTitle() != null && item.koTitle().contains(shortName)) {
            return true;
        }
        if (item.koKeyWord() != null && item.koKeyWord().contains(shortName)) {
            return true;
        }
        return false;
    }

    private boolean isEmptyResponse(PhotoAwardApiResponse response) {
        if (response == null || response.response() == null
                || response.response().body() == null
                || response.response().body().items() == null
                || response.response().body().items().item() == null) {
            return true;
        }
        return response.response().body().items().item().isEmpty();
    }

    private Optional<Coordinate> geocode(String koFilmst) {
        if (koFilmst == null || koFilmst.isBlank()) {
            return Optional.empty();
        }

        // 1. 촬영지 전체 텍스트로 카카오 로컬 검색
        Optional<Coordinate> coord = kakaoLocalSearchClient.searchRegionCoordinate(koFilmst);
        if (coord.isPresent()) {
            return coord;
        }

        // 2. 쉼표(,) 구분자가 있을 경우 세부 지명 우선 재시도 (예: "서울특별시 종로구 명륜3가, 문묘와 성균관" -> "문묘와 성균관")
        String[] parts = koFilmst.split(",");
        if (parts.length > 1) {
            for (int i = parts.length - 1; i >= 0; i--) {
                String part = parts[i].trim();
                if (part.length() >= 2) {
                    coord = kakaoLocalSearchClient.searchRegionCoordinate(part);
                    if (coord.isPresent()) {
                        return coord;
                    }
                }
            }
        }

        return Optional.empty();
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
