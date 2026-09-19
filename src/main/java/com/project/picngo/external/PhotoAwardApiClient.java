package com.project.picngo.external;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.picngo.external.dto.LdongCodeApiResponse;
import com.project.picngo.external.dto.PhotoAwardApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.DefaultUriBuilderFactory;

import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 한국관광공사 관광사진 공모전 수상작 정보 Open API 클라이언트.
 */
@Slf4j
@Component
public class PhotoAwardApiClient {

    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(12);
    private static final Pattern XML_AUTH_MSG_PATTERN = Pattern.compile("<returnAuthMsg>(.*?)</returnAuthMsg>");
    private static final Pattern XML_ERR_MSG_PATTERN = Pattern.compile("<errMsg>(.*?)</errMsg>");
    private static final Pattern XML_REASON_CODE_PATTERN = Pattern.compile("<returnReasonCode>(.*?)</returnReasonCode>");

    private final WebClient webClient;
    private final String serviceKey;
    private final ObjectMapper objectMapper;

    public PhotoAwardApiClient(
            WebClient.Builder builder,
            @Value("${photo-award.api.key:${PUBLIC_DATA_SERVICE_KEY:}}") String serviceKey,
            @Value("${photo-award.api.base-url:https://apis.data.go.kr/B551011/PhokoAwrdService}") String baseUrl
    ) {
        DefaultUriBuilderFactory factory = new DefaultUriBuilderFactory(baseUrl);
        factory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.NONE);
        this.webClient = builder.uriBuilderFactory(factory).build();
        this.serviceKey = serviceKey != null ? serviceKey.trim() : "";
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * 관광사진 공모전 수상작 목록 검색 (/phokoAwrdList)
     * 법정동 시도 코드(lDongRegnCd) 또는 검색 키워드로 수상작 목록을 조회합니다.
     */
    public PhotoAwardApiResponse getPhotoAwardList(Integer lDongRegnCd, String keyword, int pageNo, int numOfRows) {
        validateServiceKey();
        try {
            String rawResponse = webClient.get()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder.path("/phokoAwrdList")
                                .queryParam("serviceKey", serviceKey)
                                .queryParam("MobileOS", "ETC")
                                .queryParam("MobileApp", "picngo")
                                .queryParam("_type", "json")
                                .queryParam("arrange", "C")
                                .queryParam("pageNo", pageNo)
                                .queryParam("numOfRows", numOfRows);
                        if (lDongRegnCd != null) {
                            builder.queryParam("lDongRegnCd", lDongRegnCd);
                        }
                        if (keyword != null && !keyword.isBlank()) {
                            builder.queryParam("keyword", keyword.trim());
                        }
                        URI finalUri = builder.build();
                        log.info("[PhotoAwardApiClient] GET 호출: {}", maskKey(finalUri));
                        return finalUri;
                    })
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(CALL_TIMEOUT)
                    .block();

            return parseAndValidateResponse(rawResponse, "phokoAwrdList (lDongRegnCd=" + lDongRegnCd + ", keyword=" + keyword + ", page=" + pageNo + ")");
        } catch (WebClientResponseException e) {
            log.error("[PhotoAwardApiClient] phokoAwrdList HTTP 오류 (lDongRegnCd={}, keyword={}, page={}): {} - {}",
                    lDongRegnCd, keyword, pageNo, e.getStatusCode(), e.getResponseBodyAsString());
            throw new IllegalStateException("사진공모전 API HTTP 오류: " + e.getStatusCode() + " " + e.getResponseBodyAsString(), e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("[PhotoAwardApiClient] phokoAwrdList 호출 실패 (lDongRegnCd={}, keyword={}, page={}): {}",
                    lDongRegnCd, keyword, pageNo, e.getMessage());
            throw new IllegalStateException("사진공모전 API 호출 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 관광사진 공모전 수상작 동기화 목록 조회 (/phokoAwrdSyncList)
     * 표출 여부(showflag=1) 및 최신 수정일순 동기화 목록을 조회합니다.
     */
    public PhotoAwardApiResponse getPhotoAwardSyncList(Integer lDongRegnCd, int pageNo, int numOfRows) {
        validateServiceKey();
        try {
            String rawResponse = webClient.get()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder.path("/phokoAwrdSyncList")
                                .queryParam("serviceKey", serviceKey)
                                .queryParam("MobileOS", "ETC")
                                .queryParam("MobileApp", "picngo")
                                .queryParam("_type", "json")
                                .queryParam("arrange", "C")
                                .queryParam("showflag", "1")
                                .queryParam("pageNo", pageNo)
                                .queryParam("numOfRows", numOfRows);
                        if (lDongRegnCd != null) {
                            builder.queryParam("lDongRegnCd", lDongRegnCd);
                        }
                        URI finalUri = builder.build();
                        log.info("[PhotoAwardApiClient] GET 호출: {}", maskKey(finalUri));
                        return finalUri;
                    })
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(CALL_TIMEOUT)
                    .block();

            return parseAndValidateResponse(rawResponse, "phokoAwrdSyncList (lDongRegnCd=" + lDongRegnCd + ", page=" + pageNo + ")");
        } catch (WebClientResponseException e) {
            log.error("[PhotoAwardApiClient] phokoAwrdSyncList HTTP 오류 (lDongRegnCd={}, page={}): {} - {}",
                    lDongRegnCd, pageNo, e.getStatusCode(), e.getResponseBodyAsString());
            throw new IllegalStateException("사진공모전 동기화 API HTTP 오류: " + e.getStatusCode() + " " + e.getResponseBodyAsString(), e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("[PhotoAwardApiClient] phokoAwrdSyncList 호출 실패 (lDongRegnCd={}, page={}): {}",
                    lDongRegnCd, pageNo, e.getMessage());
            throw new IllegalStateException("사진공모전 동기화 API 호출 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 법정동 코드 목록 조회 (/ldongCode)
     */
    public LdongCodeApiResponse getLdongCodeList(int pageNo, int numOfRows) {
        validateServiceKey();
        try {
            String rawResponse = webClient.get()
                    .uri(uriBuilder -> {
                        URI finalUri = uriBuilder.path("/ldongCode")
                                .queryParam("serviceKey", serviceKey)
                                .queryParam("MobileOS", "ETC")
                                .queryParam("MobileApp", "picngo")
                                .queryParam("_type", "json")
                                .queryParam("pageNo", pageNo)
                                .queryParam("numOfRows", numOfRows)
                                .build();
                        log.info("[PhotoAwardApiClient] GET 호출: {}", maskKey(finalUri));
                        return finalUri;
                    })
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(CALL_TIMEOUT)
                    .block();

            if (rawResponse == null || rawResponse.isBlank()) {
                return null;
            }
            if (rawResponse.trim().startsWith("<")) {
                String xmlErr = extractXmlError(rawResponse);
                log.error("[PhotoAwardApiClient] ldongCode XML 에러 수신: {}", xmlErr);
                throw new IllegalStateException("공공데이터포털 ldongCode 에러: " + xmlErr);
            }
            return objectMapper.readValue(rawResponse, LdongCodeApiResponse.class);
        } catch (Exception e) {
            log.warn("[PhotoAwardApiClient] 법정동 코드 목록 조회 실패: {}", e.getMessage());
            return null;
        }
    }

    private PhotoAwardApiResponse parseAndValidateResponse(String raw, String context) {
        if (raw == null || raw.isBlank()) {
            log.warn("[PhotoAwardApiClient] {} 응답이 비어있습니다 (null/blank)", context);
            return emptyResponse();
        }

        String trimmed = raw.trim();
        log.info("[PhotoAwardApiClient] {} 수신 응답: {}", context,
                trimmed.length() > 500 ? trimmed.substring(0, 500) + "...(생략)" : trimmed);

        // 공공데이터포털 인증/시스템 에러는 XML 형태로 반환됨
        if (trimmed.startsWith("<")) {
            String xmlErr = extractXmlError(trimmed);
            log.error("[PhotoAwardApiClient] {} 공공데이터포털 XML 에러 응답 수신: {}", context, xmlErr);
            throw new IllegalStateException("공공데이터포털 API 에러: " + xmlErr);
        }

        try {
            PhotoAwardApiResponse response = objectMapper.readValue(trimmed, PhotoAwardApiResponse.class);
            if (response != null && response.response() != null && response.response().header() != null) {
                String resultCode = response.response().header().resultCode();
                String resultMsg = response.response().header().resultMsg();
                if (resultCode != null && !"0000".equals(resultCode)) {
                    // 03: NODATA_ERROR (해당 조건의 데이터 없음) -> 정상적인 빈 결과로 취급
                    if ("03".equals(resultCode)) {
                        log.info("[PhotoAwardApiClient] {} 데이터 없음(NODATA_ERROR, code=03)", context);
                        return emptyResponse();
                    }
                    log.error("[PhotoAwardApiClient] {} 에러 응답 수신: resultCode={}, resultMsg={}", context, resultCode, resultMsg);
                    throw new IllegalStateException(String.format("공공데이터포털 API 에러 [%s]: %s", resultCode, resultMsg));
                }
            }
            return response;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("[PhotoAwardApiClient] {} JSON 역직렬화 실패: raw={}, error={}", context, trimmed, e.getMessage());
            throw new IllegalStateException("사진공모전 API 응답 파싱 실패: " + e.getMessage(), e);
        }
    }

    private void validateServiceKey() {
        if (serviceKey == null || serviceKey.isBlank()) {
            throw new IllegalStateException("사진공모전 API 인증키(serviceKey)가 설정되지 않았습니다. PHOTO_AWARD_SERVICE_KEY 또는 PUBLIC_DATA_SERVICE_KEY 환경변수를 확인하세요.");
        }
    }

    private String extractXmlError(String xml) {
        StringBuilder sb = new StringBuilder();
        Matcher authMatcher = XML_AUTH_MSG_PATTERN.matcher(xml);
        Matcher errMatcher = XML_ERR_MSG_PATTERN.matcher(xml);
        Matcher codeMatcher = XML_REASON_CODE_PATTERN.matcher(xml);

        if (authMatcher.find()) {
            sb.append(authMatcher.group(1));
        }
        if (codeMatcher.find()) {
            sb.append(" (코드: ").append(codeMatcher.group(1)).append(")");
        }
        if (errMatcher.find()) {
            if (sb.length() > 0) sb.append(" - ");
            sb.append(errMatcher.group(1));
        }

        if (sb.length() == 0) {
            return xml.substring(0, Math.min(xml.length(), 200));
        }
        return sb.toString();
    }

    private String maskKey(URI uri) {
        if (uri == null) return "";
        return uri.toString().replaceAll("serviceKey=[^&]+", "serviceKey=***");
    }

    private PhotoAwardApiResponse emptyResponse() {
        return new PhotoAwardApiResponse(
                new PhotoAwardApiResponse.Response(
                        new PhotoAwardApiResponse.Header("0000", "OK"),
                        new PhotoAwardApiResponse.Body(
                                new PhotoAwardApiResponse.Items(Collections.emptyList()),
                                0, 1, 0
                        )
                )
        );
    }
}
