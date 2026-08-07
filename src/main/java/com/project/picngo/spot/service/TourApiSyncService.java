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
        return sync(areaCode, 1, Integer.MAX_VALUE);
    }

    public int sync(int areaCode, int startPage, int endPage) {
        int pageNo = startPage;
        int saved = 0;
        int totalCount = Integer.MAX_VALUE;

        while (pageNo <= endPage && (pageNo - 1) * PAGE_SIZE < totalCount) {
            TourApiResponse response = tourApiClient.getAreaBasedListRaw(areaCode, pageNo, PAGE_SIZE);
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
                IntroItem intro = tourApiClient.getDetailIntro(item.contentid());
                sleep(500);
                List<ImageItem> images = tourApiClient.getDetailImages(item.contentid());
                sleep(500);

                spotUpsertService.upsertSpot(item, detail, intro, images);
                saved++;
            }

            log.info("areaCode={} page={}/{} 저장중 ({}건)", areaCode, pageNo,
                    (int) Math.ceil((double) totalCount / PAGE_SIZE), saved);
            pageNo++;
        }

        log.info("TourAPI 동기화 완료: areaCode={}, 총 {}건 처리", areaCode, saved);
        return saved;
    }

    public int syncAll() {
        int[] areaCodes = {1, 2, 3, 4, 5, 6, 7, 8, 31, 32, 33, 34, 35, 36, 37, 38, 39};
        int total = 0;
        for (int areaCode : areaCodes) {
            total += sync(areaCode);
        }
        log.info("TourAPI 전체 지역 동기화 완료: 총 {}건 처리", total);
        return total;
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
