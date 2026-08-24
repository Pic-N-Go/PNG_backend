package com.project.picngo.external;

import com.project.picngo.external.dto.TourApiImageResponse;
import com.project.picngo.external.dto.TourApiImageResponse.ImageItem;
import com.project.picngo.external.dto.TourApiIntroResponse;
import com.project.picngo.external.dto.TourApiIntroResponse.IntroItem;
import com.project.picngo.external.dto.TourApiResponse;
import com.project.picngo.external.dto.TourApiResponse.Item;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.DefaultUriBuilderFactory;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

/**
 * 한국관광공사 관광정보 Open API 클라이언트.
 *
 * 다른 외부 API 클라이언트와 달리 서킷브레이커를 달지 않았다.
 * 이 클라이언트는 유저 요청이 아니라 관리자가 수동으로 트리거하는 배치(TourApiSyncService)에서만
 * 쓰여 스레드 풀 고갈 위험이 없고, 오히려 서킷이 열리면 상세 조회가 전부 null을 돌려주는데
 * 동기화 루프는 그걸 건너뛰고 계속 돌기 때문에 "빈 스팟 수천 건 저장 후 성공 보고"가 된다.
 * 느리지만 정확한 배치가 빠르지만 데이터가 망가지는 배치로 바뀌는 셈이다.
 *
 * 대신 타임아웃은 반드시 필요하다. /tour-api/sync는 동기 요청이라
 * 타임아웃이 없으면 관광공사 API가 응답하지 않을 때 요청이 무한정 매달린다.
 */
@Slf4j
@Component
public class TourApiClient {

    // 전국 동기화는 스팟 수만큼 호출이 누적되므로 개별 호출은 넉넉하되 유한해야 한다.
    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(10);

    private final WebClient webClient;
    private final String serviceKey;

    public TourApiClient(WebClient.Builder builder,
                         @Value("${tour.api.key}") String serviceKey, // ponytail: tour.api.key → PUBLIC_DATA_SERVICE_KEY 경유
                         @Value("${tour.api.base-url}") String baseUrl) {
        // 공공데이터포털 서비스키는 이미 URL 인코딩된 상태로 발급됨 → 이중 인코딩 방지
        DefaultUriBuilderFactory factory = new DefaultUriBuilderFactory(baseUrl);
        factory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.NONE);
        this.webClient = builder.uriBuilderFactory(factory).build();
        this.serviceKey = serviceKey;
    }

    public TourApiResponse getAreaBasedListRaw(Integer contentTypeId, Integer lDongRegnCd, String lclsSystm3, int pageNo, int numOfRows) {
        try {
            return webClient.get()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder.path("/areaBasedList2")
                                .queryParam("serviceKey", serviceKey)
                                .queryParam("MobileOS", "ETC")
                                .queryParam("MobileApp", "picngo")
                                .queryParam("_type", "json")
                                .queryParam("pageNo", pageNo)
                                .queryParam("numOfRows", numOfRows);

                        if (contentTypeId != null) {
                            builder.queryParam("contentTypeId", contentTypeId);
                        }
                        if (lDongRegnCd != null) {
                            builder.queryParam("lDongRegnCd", lDongRegnCd);
                        }
                        if (lclsSystm3 != null && !lclsSystm3.isBlank()) {
                            builder.queryParam("lclsSystm3", lclsSystm3);
                        }
                        return builder.build();
                    })
                    .retrieve()
                    .bodyToMono(TourApiResponse.class)
                    .timeout(CALL_TIMEOUT)
                    .block();
        } catch (Exception e) {
            log.warn("TourAPI areaBasedList 호출 실패: {}", e.getMessage());
        }
        return null;
    }

    public TourApiResponse getAreaBasedListRaw(Integer contentTypeId, Integer lDongRegnCd, int pageNo, int numOfRows) {
        return getAreaBasedListRaw(contentTypeId, lDongRegnCd, null, pageNo, numOfRows);
    }

    public TourApiResponse getAreaBasedListRaw(int areaCode, int pageNo, int numOfRows) {
        return getAreaBasedListRaw(12, areaCode, null, pageNo, numOfRows);
    }

    public TourApiResponse getFestivalList(String eventStartDate, Integer lDongRegnCd, int pageNo, int numOfRows) {
        try {
            return webClient.get()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder.path("/searchFestival2")
                                .queryParam("serviceKey", serviceKey)
                                .queryParam("MobileOS", "ETC")
                                .queryParam("MobileApp", "picngo")
                                .queryParam("_type", "json")
                                .queryParam("eventStartDate", eventStartDate)
                                .queryParam("pageNo", pageNo)
                                .queryParam("numOfRows", numOfRows);

                        if (lDongRegnCd != null) {
                            builder.queryParam("lDongRegnCd", lDongRegnCd);
                        }
                        return builder.build();
                    })
                    .retrieve()
                    .bodyToMono(TourApiResponse.class)
                    .timeout(CALL_TIMEOUT)
                    .block();
        } catch (Exception e) {
            log.warn("TourAPI searchFestival2 호출 실패: {}", e.getMessage());
        }
        return null;
    }

    public Item getDetailCommon(String contentId) {
        try {
            TourApiResponse response = webClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/detailCommon2")
                            .queryParam("serviceKey", serviceKey)
                            .queryParam("MobileOS", "ETC")
                            .queryParam("MobileApp", "picngo")
                            .queryParam("_type", "json")
                            .queryParam("contentId", contentId)
                            .build())
                    .retrieve()
                    .bodyToMono(TourApiResponse.class)
                    .timeout(CALL_TIMEOUT)
                    .block();

            if (response != null && response.response() != null
                    && response.response().body() != null
                    && response.response().body().items() != null) {
                List<Item> items = response.response().body().items().item();
                if (items != null && !items.isEmpty()) return items.get(0);
            }
        } catch (Exception e) {
            log.warn("TourAPI detailCommon 호출 실패 contentId={}: {}", contentId, e.getMessage());
        }
        return null;
    }

    public IntroItem getDetailIntro(String contentId, int contentTypeId) {
        try {
            TourApiIntroResponse response = webClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/detailIntro2")
                            .queryParam("serviceKey", serviceKey)
                            .queryParam("MobileOS", "ETC")
                            .queryParam("MobileApp", "picngo")
                            .queryParam("_type", "json")
                            .queryParam("contentId", contentId)
                            .queryParam("contentTypeId", contentTypeId)
                            .build())
                    .retrieve()
                    .bodyToMono(TourApiIntroResponse.class)
                    .timeout(CALL_TIMEOUT)
                    .block();

            if (response != null && response.response() != null
                    && response.response().body() != null
                    && response.response().body().items() != null) {
                List<IntroItem> items = response.response().body().items().item();
                if (items != null && !items.isEmpty()) return items.get(0);
            }
        } catch (Exception e) {
            log.warn("TourAPI detailIntro 호출 실패 contentId={}, contentTypeId={}: {}", contentId, contentTypeId, e.getMessage());
        }
        return null;
    }

    public IntroItem getDetailIntro(String contentId) {
        return getDetailIntro(contentId, 12);
    }

    public List<ImageItem> getDetailImages(String contentId) {
        try {
            TourApiImageResponse response = webClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/detailImage2")
                            .queryParam("serviceKey", serviceKey)
                            .queryParam("MobileOS", "ETC")
                            .queryParam("MobileApp", "picngo")
                            .queryParam("_type", "json")
                            .queryParam("contentId", contentId)
                            .queryParam("imageYN", "Y")
                            .build())
                    .retrieve()
                    .bodyToMono(TourApiImageResponse.class)
                    .timeout(CALL_TIMEOUT)
                    .block();

            if (response != null && response.response() != null
                    && response.response().body() != null
                    && response.response().body().items() != null) {
                List<ImageItem> items = response.response().body().items().item();
                return items != null ? items : Collections.emptyList();
            }
        } catch (Exception e) {
            log.warn("TourAPI detailImage 호출 실패 contentId={}: {}", contentId, e.getMessage());
        }
        return Collections.emptyList();
    }
}
