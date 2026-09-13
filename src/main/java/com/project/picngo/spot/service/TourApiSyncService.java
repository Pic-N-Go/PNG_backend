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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int[] TARGET_TYPES = {12, 14, 15, 39}; // 관광지(12), 문화시설(14), 축제/행사(15), 카페(39)

    public int sync(int areaCode) {
        return sync(areaCode, 1, Integer.MAX_VALUE);
    }

    public int sync(int areaCode, int startPage, int endPage) {
        int total = 0;
        for (int type : TARGET_TYPES) {
            total += syncType(type, areaCode, startPage, endPage);
        }
        log.info("[TourApiSyncService] 지역(areaCode: {}) 전체 타입 동기화 완료: 총 {}건 저장", areaCode, total);
        return total;
    }

    /**
     * 상세 조회 3종이 모두 비었으면 API 장애로 보고 저장하지 않는다.
     *
     * 상세 조회 클라이언트는 실패를 예외로 던지지 않고 null(이미지는 빈 목록)을 돌려준다.
     * 그대로 저장하면 개요·소개·이미지가 빠진 스팟이 쌓이면서도 "완료"로 집계되는데,
     * 배치에서는 이런 조용한 부분 실패가 가장 위험하다.
     *
     * 셋 중 일부만 비는 것은 원래 상세가 없는 스팟일 수 있으므로 기존대로 저장한다.
     * 셋이 전부 빈 경우만 장애로 본다.
     */
    private boolean isDetailFetchFailed(Item detail, IntroItem intro, List<ImageItem> images) {
        return detail == null && intro == null && (images == null || images.isEmpty());
    }

    public int syncType(int contentTypeId, Integer areaCode, int startPage, int endPage) {
        int pageNo = startPage;
        int saved = 0;
        int skipped = 0;
        int failed = 0;
        int totalCount = Integer.MAX_VALUE;
        String todayStr = LocalDate.now().format(YYYYMMDD);

        while (pageNo <= endPage && (pageNo - 1) * PAGE_SIZE < totalCount) {
            // 축제(15)는 searchFestival2 전용 API, 카페(39)는 FD050100 소분류 필터 적용
            TourApiResponse response = (contentTypeId == 15)
                    ? tourApiClient.getFestivalList(todayStr, areaCode, null, pageNo, PAGE_SIZE)
                    : (contentTypeId == 39)
                    ? tourApiClient.getAreaBasedListRaw(contentTypeId, areaCode, "FD050100", pageNo, PAGE_SIZE)
                    : tourApiClient.getAreaBasedListRaw(contentTypeId, areaCode, pageNo, PAGE_SIZE);

            if (response == null || response.response() == null
                    || response.response().body() == null
                    || response.response().body().items() == null) {
                log.info("[TourApiSyncService] 데이터 없음 또는 페이지 종료: contentTypeId={}, areaCode={}, page={}",
                        contentTypeId, areaCode, pageNo);
                break;
            }

            totalCount = response.response().body().totalCount();
            List<Item> items = response.response().body().items().item();
            if (items == null || items.isEmpty()) {
                log.info("[TourApiSyncService] 항목 없음: contentTypeId={}, areaCode={}, page={}",
                        contentTypeId, areaCode, pageNo);
                break;
            }

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
                    skipped++;
                    log.debug("[TourApiSyncService] 기존 스팟 건너뜀: contentId={}, title={}", item.contentid(), item.title());
                    continue;
                }

                // API 호출 + sleep은 트랜잭션 밖에서 처리
                Item detail = tourApiClient.getDetailCommon(item.contentid());
                sleep(API_CALL_DELAY_MS);
                IntroItem intro = tourApiClient.getDetailIntro(item.contentid(), contentTypeId);
                sleep(API_CALL_DELAY_MS);
                List<ImageItem> images = tourApiClient.getDetailImages(item.contentid());
                sleep(API_CALL_DELAY_MS);

                if (isDetailFetchFailed(detail, intro, images)) {
                    failed++;
                    log.warn("[TourApiSyncService] 상세 조회 전부 실패, 저장 건너뜀: contentId={}, title={}",
                            item.contentid(), item.title());
                    continue;
                }

                boolean processed = spotUpsertService.upsertSpot(item, detail, intro, images);
                if (processed) {
                    saved++;
                }

                if (syncStatusManager != null) {
                    syncStatusManager.updateProgress(
                            saved,
                            totalCount,
                            String.format("동기화 진행 중 (type: %d, 지역: %s) - 신규: %d건, 건너뜀: %d건, 실패: %d건 / 총 %d건",
                                    contentTypeId, areaCode != null ? areaCode : "전체", saved, skipped, failed, totalCount)
                    );
                }
            }

            log.info("[TourApiSyncService] contentTypeId={}, areaCode={}, page={}/{} (신규 {}건, 건너뜀 {}건, 실패 {}건)",
                    contentTypeId, areaCode, pageNo,
                    (int) Math.ceil((double) totalCount / PAGE_SIZE), saved, skipped, failed);
            pageNo++;
        }

        if (failed > 0) {
            log.warn("[TourApiSyncService] contentTypeId={} 상세 조회 실패 {}건 - 해당 스팟은 저장하지 않았다. "
                    + "관광공사 API 장애일 수 있으니 복구 후 재동기화할 것", contentTypeId, failed);
        }
        log.info("[TourApiSyncService] contentTypeId={} 동기화 완료: areaCode={}, 신규 {}건 저장, 기존 건너뜀 {}건, 상세 실패 {}건",
                contentTypeId, areaCode, saved, skipped, failed);
        return saved;
    }

    public int syncAllTypes(int maxPagesPerType) {
        int total = 0;
        for (int type : TARGET_TYPES) {
            total += syncType(type, null, 1, maxPagesPerType);
        }
        log.info("[TourApiSyncService] 전체 타입 전국 동기화 완료: 총 {}건 저장", total);
        return total;
    }

    public int syncSample(int countPerType) {
        int totalSaved = 0;
        int totalFailed = 0;
        String todayStr = LocalDate.now().format(YYYYMMDD);

        for (int type : TARGET_TYPES) {
            TourApiResponse response;
            try {
                if (type == 15) {
                    response = tourApiClient.getFestivalList(todayStr, null, null, 1, countPerType);
                } else if (type == 39) {
                    response = tourApiClient.getAreaBasedListRaw(type, null, "FD050100", 1, countPerType * 2);
                } else {
                    response = tourApiClient.getAreaBasedListRaw(type, null, 1, countPerType);
                }
            } catch (Exception e) {
                log.warn("[TourApiSyncService] 샘플 수집 중 API 호출 실패 (type={}): {}", type, e.getMessage());
                continue;
            }

            if (response == null || response.response() == null
                    || response.response().body() == null
                    || response.response().body().items() == null) {
                log.warn("[TourApiSyncService] 샘플 수집 실패 (응답 비어있음): type={}", type);
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
                    log.debug("[TourApiSyncService] 샘플 동기화 중 기존 스팟 건너뜀: contentId={}, title={}", item.contentid(), item.title());
                    continue;
                }

                Item detail = tourApiClient.getDetailCommon(item.contentid());
                sleep(API_CALL_DELAY_MS);
                IntroItem intro = tourApiClient.getDetailIntro(item.contentid(), type);
                sleep(API_CALL_DELAY_MS);
                List<ImageItem> images = tourApiClient.getDetailImages(item.contentid());
                sleep(API_CALL_DELAY_MS);

                if (isDetailFetchFailed(detail, intro, images)) {
                    totalFailed++;
                    log.warn("[TourApiSyncService] 샘플 수집 중 상세 조회 전부 실패, 저장 건너뜀: contentId={}, title={}",
                            item.contentid(), item.title());
                    continue;
                }

                boolean processed = spotUpsertService.upsertSpot(item, detail, intro, images);
                if (processed) {
                    savedForType++;
                    totalSaved++;
                    log.info("[TourApiSyncService] 샘플 저장 완료 [type={}]: {} (contentId={})", type, item.title(), item.contentid());
                }
            }
        }
        if (totalFailed > 0) {
            log.warn("[TourApiSyncService] 샘플 동기화 중 상세 조회 실패 {}건 - 해당 스팟은 저장하지 않았다", totalFailed);
        }
        log.info("[TourApiSyncService] TourAPI 샘플 동기화 완료: 총 {}건 저장, 상세 실패 {}건", totalSaved, totalFailed);
        return totalSaved;
    }

    public int syncAll() {
        return syncAllTypes(Integer.MAX_VALUE);
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
