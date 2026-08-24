package com.project.picngo.spot.service;

import com.project.picngo.external.TourApiClient;
import com.project.picngo.external.dto.TourApiImageResponse.ImageItem;
import com.project.picngo.external.dto.TourApiIntroResponse.IntroItem;
import com.project.picngo.external.dto.TourApiResponse;
import com.project.picngo.external.dto.TourApiResponse.Item;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TourApiSyncService {

    private final TourApiClient tourApiClient;
    private final SpotUpsertService spotUpsertService;

    private static final int PAGE_SIZE = 100;

    public int sync(int areaCode) {
        return syncType(12, areaCode, 1, Integer.MAX_VALUE);
    }

    public int sync(int areaCode, int startPage, int endPage) {
        return syncType(12, areaCode, startPage, endPage);
    }

    public int syncType(int contentTypeId, Integer lDongRegnCd, int startPage, int endPage) {
        int pageNo = startPage;
        int saved = 0;
        int totalCount = Integer.MAX_VALUE;

        while (pageNo <= endPage && (pageNo - 1) * PAGE_SIZE < totalCount) {
            TourApiResponse response = tourApiClient.getAreaBasedListRaw(contentTypeId, lDongRegnCd, pageNo, PAGE_SIZE);
            if (response == null || response.response() == null
                    || response.response().body() == null
                    || response.response().body().items() == null) break;

            totalCount = response.response().body().totalCount();
            List<Item> items = response.response().body().items().item();
            if (items == null || items.isEmpty()) break;

            for (Item item : items) {
                // API 호출 + sleep은 트랜잭션 밖에서 처리
                Item detail = tourApiClient.getDetailCommon(item.contentid());
                sleep(500);
                IntroItem intro = tourApiClient.getDetailIntro(item.contentid(), contentTypeId);
                sleep(500);
                List<ImageItem> images = tourApiClient.getDetailImages(item.contentid());
                sleep(500);

                spotUpsertService.upsertSpot(item, detail, intro, images);
                saved++;
            }

            log.info("contentTypeId={} lDongRegnCd={} page={}/{} 저장중 ({}건)",
                    contentTypeId, lDongRegnCd, pageNo,
                    (int) Math.ceil((double) totalCount / PAGE_SIZE), saved);
            pageNo++;
        }

        log.info("TourAPI 동기화 완료: contentTypeId={}, lDongRegnCd={}, 총 {}건 처리", contentTypeId, lDongRegnCd, saved);
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

            int savedForType = 0;
            for (Item item : items) {
                if (savedForType >= countPerType) break;

                Item detail = tourApiClient.getDetailCommon(item.contentid());
                sleep(200);
                IntroItem intro = tourApiClient.getDetailIntro(item.contentid(), type);
                sleep(200);
                List<ImageItem> images = tourApiClient.getDetailImages(item.contentid());
                sleep(200);

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
