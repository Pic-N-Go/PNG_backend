package com.project.picngo.external;

import com.project.picngo.external.dto.PetTourSyncListResponse;
import com.project.picngo.external.dto.PetTourDetailResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.DefaultUriBuilderFactory;

import java.time.Duration;
import java.util.List;

@Slf4j
@Component
public class PetTourApiClient {

    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(30);
    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;

    private final WebClient webClient;
    private final String serviceKey;

    public PetTourApiClient(
            WebClient.Builder builder,
            @Value("${tour.api.pet-key}") String serviceKey,
            @Value("${tour.api.pet-base-url}") String baseUrl
    ) {
        DefaultUriBuilderFactory factory = new DefaultUriBuilderFactory(baseUrl);
        factory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.NONE);
        this.webClient = builder.clone()
                .uriBuilderFactory(factory)
                .codecs(configurer -> configurer.defaultCodecs()
                        .maxInMemorySize(MAX_RESPONSE_BYTES))
                .build();
        this.serviceKey = serviceKey;
    }

    public PetTourSyncListResponse getSyncList(
            int contentTypeId,
            int pageNo,
            int numOfRows
    ) {
        return getSyncList(contentTypeId, pageNo, numOfRows, null);
    }

    public PetTourSyncListResponse getSyncList(
            int contentTypeId, int pageNo, int numOfRows, Integer legalRegionCode
    ) {
        try {
            PetTourSyncListResponse response = webClient.get()
                    .uri(uriBuilder -> {
                        var uri = uriBuilder.path("/petTourSyncList2")
                                .queryParam("serviceKey", serviceKey).queryParam("MobileOS", "ETC")
                                .queryParam("MobileApp", "picngo").queryParam("_type", "json")
                                .queryParam("pageNo", pageNo).queryParam("numOfRows", numOfRows)
                                .queryParam("contentTypeId", contentTypeId).queryParam("showflag", 1);
                        if (legalRegionCode != null) uri.queryParam("lDongRegnCd", legalRegionCode);
                        return uri.build();
                    })
                    .retrieve()
                    .bodyToMono(PetTourSyncListResponse.class)
                    .timeout(CALL_TIMEOUT)
                    .block();

            validateResponse(response);
            return response;
        } catch (WebClientResponseException e) {
            log.error("[PetTourApiClient] 목록 HTTP 오류 [status={}]: body={}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new IllegalStateException("반려동물 목록 API HTTP 오류: " + e.getStatusCode(), e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("반려동물 목록 API 호출 실패: " + e.getMessage(), e);
        }
    }

    public PetTourDetailResponse.Item getDetail(String contentId) {
        try {
            PetTourDetailResponse response = webClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/detailPetTour2")
                            .queryParam("serviceKey", serviceKey)
                            .queryParam("MobileOS", "ETC")
                            .queryParam("MobileApp", "picngo")
                            .queryParam("_type", "json")
                            .queryParam("contentId", contentId)
                            .build())
                    .retrieve()
                    .bodyToMono(PetTourDetailResponse.class)
                    .timeout(CALL_TIMEOUT)
                    .block();

            validateDetailResponse(response, contentId);
            if (response.response().body() == null || response.response().body().items() == null) {
                return null;
            }
            List<PetTourDetailResponse.Item> items = response.response().body().items().safeItems();
            return items.isEmpty() ? null : items.get(0);
        } catch (WebClientResponseException e) {
            log.warn("[PetTourApiClient] 상세 HTTP 오류 (contentId={}, status={}): {}",
                    contentId, e.getStatusCode(), e.getResponseBodyAsString());
            throw new IllegalStateException("반려동물 상세 API HTTP 오류: " + e.getStatusCode(), e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "반려동물 상세 API 호출 실패 (contentId=" + contentId + "): " + e.getMessage(), e);
        }
    }

    private void validateResponse(PetTourSyncListResponse response) {
        if (response == null || response.response() == null) {
            throw new IllegalStateException("반려동물 목록 API 응답이 비어 있습니다.");
        }
        var header = response.response().header();
        if (header != null && header.resultCode() != null && !"0000".equals(header.resultCode())) {
            throw new IllegalStateException(String.format(
                    "반려동물 목록 API 에러: [%s] %s",
                    header.resultCode(), header.resultMsg()));
        }
    }

    private void validateDetailResponse(PetTourDetailResponse response, String contentId) {
        if (response == null || response.response() == null) {
            throw new IllegalStateException("반려동물 상세 API 응답이 비어 있습니다. contentId=" + contentId);
        }
        var header = response.response().header();
        if (header != null && header.resultCode() != null && !"0000".equals(header.resultCode())) {
            throw new IllegalStateException(String.format(
                    "반려동물 상세 API 에러 (contentId=%s): [%s] %s",
                    contentId, header.resultCode(), header.resultMsg()));
        }
    }
}
