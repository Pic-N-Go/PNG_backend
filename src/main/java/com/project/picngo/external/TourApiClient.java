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

import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class TourApiClient {

    // 한국관광공사 관광정보 Open API
    private static final String BASE_URL = "https://apis.data.go.kr/B551011/KorService2";

    private final WebClient webClient;
    private final String serviceKey;

    public TourApiClient(WebClient.Builder builder, @Value("${tour.api.key}") String serviceKey) { // ponytail: tour.api.key → PUBLIC_DATA_SERVICE_KEY 경유
        // 공공데이터포털 서비스키는 이미 URL 인코딩된 상태로 발급됨 → 이중 인코딩 방지
        DefaultUriBuilderFactory factory = new DefaultUriBuilderFactory(BASE_URL);
        factory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.NONE);
        this.webClient = builder.uriBuilderFactory(factory).build();
        this.serviceKey = serviceKey;
    }

    public TourApiResponse getAreaBasedListRaw(int areaCode, int pageNo, int numOfRows) {
        try {
            return webClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/areaBasedList2")
                            .queryParam("serviceKey", serviceKey)
                            .queryParam("MobileOS", "ETC")
                            .queryParam("MobileApp", "picngo")
                            .queryParam("_type", "json")
                            .queryParam("areaCode", areaCode)
                            .queryParam("contentTypeId", 12)
                            .queryParam("pageNo", pageNo)
                            .queryParam("numOfRows", numOfRows)
                            .build())
                    .retrieve()
                    .bodyToMono(TourApiResponse.class)
                    .block();
        } catch (Exception e) {
            log.error("TourAPI areaBasedList 호출 실패", e);
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
                    .block();

            if (response != null && response.response() != null
                    && response.response().body() != null
                    && response.response().body().items() != null) {
                List<Item> items = response.response().body().items().item();
                if (items != null && !items.isEmpty()) return items.get(0);
            }
        } catch (Exception e) {
            log.error("TourAPI detailCommon 호출 실패 contentId={}", contentId, e);
        }
        return null;
    }

    public IntroItem getDetailIntro(String contentId) {
        try {
            TourApiIntroResponse response = webClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/detailIntro2")
                            .queryParam("serviceKey", serviceKey)
                            .queryParam("MobileOS", "ETC")
                            .queryParam("MobileApp", "picngo")
                            .queryParam("_type", "json")
                            .queryParam("contentId", contentId)
                            .queryParam("contentTypeId", 12)
                            .build())
                    .retrieve()
                    .bodyToMono(TourApiIntroResponse.class)
                    .block();

            if (response != null && response.response() != null
                    && response.response().body() != null
                    && response.response().body().items() != null) {
                List<IntroItem> items = response.response().body().items().item();
                if (items != null && !items.isEmpty()) return items.get(0);
            }
        } catch (Exception e) {
            log.error("TourAPI detailIntro 호출 실패 contentId={}", contentId, e);
        }
        return null;
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
                    .block();

            if (response != null && response.response() != null
                    && response.response().body() != null
                    && response.response().body().items() != null) {
                List<ImageItem> items = response.response().body().items().item();
                return items != null ? items : Collections.emptyList();
            }
        } catch (Exception e) {
            log.error("TourAPI detailImage 호출 실패 contentId={}", contentId, e);
        }
        return Collections.emptyList();
    }
}
