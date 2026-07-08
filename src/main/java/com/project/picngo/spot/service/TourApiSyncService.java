package com.project.picngo.spot.service;

import com.project.picngo.external.TourApiClient;
import com.project.picngo.external.dto.TourApiIntroResponse.IntroItem;
import com.project.picngo.external.dto.TourApiResponse;
import com.project.picngo.external.dto.TourApiResponse.Item;
import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.domain.enums.SpotSource;
import com.project.picngo.spot.domain.enums.SpotStatus;
import com.project.picngo.spot.repository.SpotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TourApiSyncService {

    private final TourApiClient tourApiClient;
    private final SpotRepository spotRepository;

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

                upsertSpot(item, detail, intro);
                saved++;
            }

            log.info("areaCode={} page={}/{} 저장중 ({}건)", areaCode, pageNo,
                    (int) Math.ceil((double) totalCount / PAGE_SIZE), saved);
            pageNo++;
        }

        log.info("TourAPI 동기화 완료: areaCode={}, 총 {}건 처리", areaCode, saved);
        return saved;
    }

    @Transactional
    public void upsertSpot(Item item, Item detail, IntroItem intro) {
        spotRepository.findByTourContentId(item.contentid()).ifPresentOrElse(
                spot -> spot.updateFromTourApi(
                        detail != null ? detail.overview() : null,
                        intro != null ? intro.parking() : null,
                        intro != null ? intro.usetime() : null,
                        intro != null ? intro.restdate() : null,
                        intro != null ? intro.infocenter() : null,
                        intro != null ? intro.chkhandicap() : null,
                        intro != null ? intro.chkbabycarriage() : null,
                        intro != null ? intro.chkpet() : null
                ),
                () -> spotRepository.save(Spot.builder()
                        .name(item.title())
                        .address(trim(item.addr1()) + (item.addr2() != null ? " " + item.addr2().trim() : ""))
                        .zipcode(item.zipcode())
                        .overview(detail != null ? detail.overview() : null)
                        .latitude(parseDouble(item.mapy()))
                        .longitude(parseDouble(item.mapx()))
                        .category(item.cat1())
                        .cat3(item.cat3())
                        .source(SpotSource.TOUR_API)
                        .badge(true)
                        .tourContentId(item.contentid())
                        .imageUrl(item.firstimage())
                        .thumbnailUrl(item.firstimage2())
                        .status(SpotStatus.APPROVED)
                        .parking(intro != null ? intro.parking() : null)
                        .usetime(intro != null ? intro.usetime() : null)
                        .restdate(intro != null ? intro.restdate() : null)
                        .infocenter(intro != null ? intro.infocenter() : null)
                        .strollerAccess(intro != null ? intro.chkbabycarriage() : null)
                        .petFriendly(intro != null ? intro.chkpet() : null)
                        .wheelchairAccess(intro != null ? intro.chkhandicap() : null)
                        .build())
        );
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

    private Double parseDouble(String value) {
        try { return value != null ? Double.parseDouble(value) : 0.0; }
        catch (NumberFormatException e) { return 0.0; }
    }

    private String trim(String value) {
        return value != null ? value.trim() : "";
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
