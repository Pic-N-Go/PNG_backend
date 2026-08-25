package com.project.picngo.spot.service;

import com.project.picngo.external.TourApiClient;
import com.project.picngo.external.dto.TourApiImageResponse.ImageItem;
import com.project.picngo.external.dto.TourApiIntroResponse.IntroItem;
import com.project.picngo.external.dto.TourApiResponse;
import com.project.picngo.external.dto.TourApiResponse.Item;
import com.project.picngo.spot.repository.SpotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class TourApiSyncService {

    private final TourApiClient tourApiClient;
    private final SpotUpsertService spotUpsertService;
    private final SpotRepository spotRepository;
    private final TourApiSyncStatusManager syncStatusManager;

    private static final int PAGE_SIZE = 100;
    private static final long API_CALL_DELAY_MS = 150;

    public int sync(int areaCode) {
        return syncType(12, areaCode, 1, Integer.MAX_VALUE);
    }

    public int sync(int areaCode, int startPage, int endPage) {
        return syncType(12, areaCode, startPage, endPage);
    }

    public int syncType(int contentTypeId, Integer areaCode, int startPage, int endPage) {
        int pageNo = startPage;
        int saved = 0;
        int totalCount = Integer.MAX_VALUE;

        while (pageNo <= endPage && (pageNo - 1) * PAGE_SIZE < totalCount) {
            TourApiResponse response = tourApiClient.getAreaBasedListRaw(contentTypeId, areaCode, pageNo, PAGE_SIZE);
            if (response == null || response.response() == null
                    || response.response().body() == null
                    || response.response().body().items() == null) break;

            totalCount = response.response().body().totalCount();
            List<Item> items = response.response().body().items().item();
            if (items == null || items.isEmpty()) break;

            // N+1 문제 없는 배치 IN 조회로 이미 존재하는 스팟 ID들을 Set으로 일괄 조회
            List<String> contentIds = items.stream()
                    .map(Item::contentid)
                    .filter(id -> id != null && !id.isBlank())
                    .toList();
            Set<String> existingIds = contentIds.isEmpty()
                    ? Collections.emptySet()
                    : spotRepository.findExistingTourContentIds(contentIds);

            for (Item item : items) {
                // 이미 DB에 존재하는 스팟은 상세 API 3회 호출(1.5초)을 즉시 스킵
                if (item.contentid() != null && existingIds.contains(item.contentid())) {
                    log.debug("이미 존재하는 스팟 건너뜁니다: contentId={}, title={}", item.contentid(), item.title());
                    continue;
                }

                // API 호출 + sleep은 트랜잭션 밖에서 처리
                Item detail = tourApiClient.getDetailCommon(item.contentid());
                sleep(API_CALL_DELAY_MS);
                IntroItem intro = tourApiClient.getDetailIntro(item.contentid(), contentTypeId);
                sleep(API_CALL_DELAY_MS);
                List<ImageItem> images = tourApiClient.getDetailImages(item.contentid());
                sleep(API_CALL_DELAY_MS);

                spotUpsertService.upsertSpot(item, detail, intro, images);
                saved++;

                if (syncStatusManager != null) {
                    syncStatusManager.updateProgress(
                            saved,
                            totalCount,
                            String.format("동기화 진행 중 (type: %d, 지역: %s) - %d/%d건",
                                    contentTypeId, areaCode != null ? areaCode : "전체", saved, totalCount)
                    );
                }
            }

            log.info("contentTypeId={} areaCode={} page={}/{} 저장중 ({}건)",
                    contentTypeId, areaCode, pageNo,
                    (int) Math.ceil((double) totalCount / PAGE_SIZE), saved);
            pageNo++;
        }

        log.info("TourAPI 동기화 완료: contentTypeId={}, areaCode={}, 총 {}건 신규/갱신 처리", contentTypeId, areaCode, saved);
        return saved;
    }

    public int syncAllTypes(int maxPagesPerType) {
        int[] targetTypes = {12, 14, 15, 39}; // 관광지, 문화시설, 축제/행사, 카페
        int total = 0;
        for (int type : targetTypes) {
            total += syncType(type, null, 1, maxPagesPerType);
        }
        log.info("TourAPI 전체 타입 전국 동기화 완료: 총 {}건 처리", total);
        return total;
    }

    public int syncSample(int countPerType) {
        int[] targetTypes = {12, 14, 15, 39}; // 관광지, 문화시설, 축제/행사, 카페
        int totalSaved = 0;

        for (int type : targetTypes) {
            TourApiResponse response = (type == 39)
                    ? tourApiClient.getAreaBasedListRaw(type, null, "FD050100", 1, countPerType * 2)
                    : tourApiClient.getAreaBasedListRaw(type, null, 1, countPerType);

            if (response == null || response.response() == null
                    || response.response().body() == null
                    || response.response().body().items() == null) {
                log.warn("샘플 수집 실패: type={}", type);
                continue;
            }

            List<Item> items = response.response().body().items().item();
            if (items == null || items.isEmpty()) continue;

            List<String> contentIds = items.stream()
                    .map(Item::contentid)
                    .filter(id -> id != null && !id.isBlank())
                    .toList();
            Set<String> existingIds = contentIds.isEmpty()
                    ? Collections.emptySet()
                    : spotRepository.findExistingTourContentIds(contentIds);

            int savedForType = 0;
            for (Item item : items) {
                if (savedForType >= countPerType) break;
                if (item.contentid() != null && existingIds.contains(item.contentid())) {
                    log.debug("샘플 동기화 중 이미 존재하는 스팟 건너뜁니다: contentId={}, title={}", item.contentid(), item.title());
                    continue;
                }

                Item detail = tourApiClient.getDetailCommon(item.contentid());
                sleep(API_CALL_DELAY_MS);
                IntroItem intro = tourApiClient.getDetailIntro(item.contentid(), type);
                sleep(API_CALL_DELAY_MS);
                List<ImageItem> images = tourApiClient.getDetailImages(item.contentid());
                sleep(API_CALL_DELAY_MS);

                spotUpsertService.upsertSpot(item, detail, intro, images);
                savedForType++;
                totalSaved++;
                log.info("샘플 저장 완료 [type={}]: {} (contentId={})", type, item.title(), item.contentid());
            }
        }
        log.info("TourAPI 샘플 동기화 완료: 총 {}건 저장", totalSaved);
        return totalSaved;
    }

    public int syncAll() {
        return syncAllTypes(Integer.MAX_VALUE);
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
