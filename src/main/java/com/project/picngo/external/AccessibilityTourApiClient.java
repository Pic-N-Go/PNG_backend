package com.project.picngo.external;

import com.project.picngo.external.dto.AccessibilityTourDetailResponse;
import com.project.picngo.external.dto.AccessibilityTourSyncListResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.DefaultUriBuilderFactory;

import java.time.Duration;

@Slf4j
@Component
public class AccessibilityTourApiClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;

    private final WebClient webClient;
    private final String serviceKey;

    public AccessibilityTourApiClient(
            WebClient.Builder builder,
            @Value("${tour.api.accessibility-key}") String serviceKey,
            @Value("${tour.api.accessibility-base-url}") String baseUrl
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

    public AccessibilityTourSyncListResponse getSyncList(int contentTypeId, int pageNo, int numOfRows) {
        return getSyncList(contentTypeId, pageNo, numOfRows, null);
    }

    public AccessibilityTourSyncListResponse getSyncList(
            int contentTypeId,
            int pageNo,
            int numOfRows,
            Integer areaCode
    ) {
        try {
            AccessibilityTourSyncListResponse response = webClient.get()
                    .uri(builder -> {
                        var uri = builder.path("/areaBasedSyncList2")
                                .queryParam("serviceKey", serviceKey)
                                .queryParam("MobileOS", "ETC")
                                .queryParam("MobileApp", "picngo")
                                .queryParam("_type", "json")
                                .queryParam("pageNo", pageNo)
                                .queryParam("numOfRows", numOfRows)
                                .queryParam("contentTypeId", contentTypeId)
                                .queryParam("showflag", 1);
                        if (areaCode != null) {
                            uri.queryParam("areaCode", areaCode);
                        }
                        return uri.build();
                    })
                    .retrieve()
                    .bodyToMono(AccessibilityTourSyncListResponse.class)
                    .block(TIMEOUT);

            validateListResponse(response);
            return response;
        } catch (WebClientResponseException e) {
            log.error("[AccessibilityTourApiClient] 목록 HTTP 오류 [status={}]: body={}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new IllegalStateException("무장애 목록 API HTTP 오류: " + e.getStatusCode(), e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("무장애 목록 API 호출 실패: " + e.getMessage(), e);
        }
    }

    public AccessibilityTourDetailResponse.Item getDetail(String contentId) {
        try {
            AccessibilityTourDetailResponse response = webClient.get()
                    .uri(builder -> builder.path("/detailWithTour2")
                            .queryParam("serviceKey", serviceKey)
                            .queryParam("MobileOS", "ETC")
                            .queryParam("MobileApp", "picngo")
                            .queryParam("_type", "json")
                            .queryParam("pageNo", 1)
                            .queryParam("numOfRows", 10)
                            .queryParam("contentId", contentId)
                            .build())
                    .retrieve()
                    .bodyToMono(AccessibilityTourDetailResponse.class)
                    .block(TIMEOUT);

            validateDetailResponse(response, contentId);
            if (response.response().body() == null || response.response().body().items() == null) {
                return null;
            }
            return response.response().body().items().safeItems().stream()
                    .findFirst()
                    .orElse(null);
        } catch (WebClientResponseException e) {
            log.error("[AccessibilityTourApiClient] 상세 HTTP 오류 (contentId={}, status={}): body={}",
                    contentId, e.getStatusCode(), e.getResponseBodyAsString());
            throw new IllegalStateException("무장애 상세 API HTTP 오류: " + e.getStatusCode(), e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "무장애 상세 API 호출 실패 (contentId=" + contentId + "): " + e.getMessage(), e);
        }
    }

    private void validateListResponse(AccessibilityTourSyncListResponse response) {
        if (response == null || response.response() == null) {
            throw new IllegalStateException("무장애 목록 API 응답이 비어 있습니다.");
        }
        validateHeader(
                response.response().header() == null ? null : response.response().header().resultCode(),
                response.response().header() == null ? null : response.response().header().resultMsg(),
                "무장애 목록 API"
        );
    }

    private void validateDetailResponse(
            AccessibilityTourDetailResponse response,
            String contentId
    ) {
        if (response == null || response.response() == null) {
            throw new IllegalStateException("무장애 상세 API 응답이 비어 있습니다. contentId=" + contentId);
        }
        validateHeader(
                response.response().header() == null ? null : response.response().header().resultCode(),
                response.response().header() == null ? null : response.response().header().resultMsg(),
                "무장애 상세 API (contentId=" + contentId + ")"
        );
    }

    private void validateHeader(String resultCode, String resultMsg, String apiName) {
        if (resultCode != null && !"0000".equals(resultCode)) {
            throw new IllegalStateException(String.format(
                    "%s 에러: [%s] %s",
                    apiName,
                    resultCode,
                    resultMsg
            ));
        }
    }
}
