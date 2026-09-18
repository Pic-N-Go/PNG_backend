package com.project.picngo.external;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.picngo.external.dto.CongestionApiResponse;
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
 * 한국관광공사 관광지 집중률 방문자 추이 예측 정보 Open API 클라이언트.
 */
@Slf4j
@Component
public class CongestionApiClient {

    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(10);
    private static final Pattern XML_AUTH_MSG_PATTERN = Pattern.compile("<returnAuthMsg>(.*?)</returnAuthMsg>");
    private static final Pattern XML_ERR_MSG_PATTERN = Pattern.compile("<errMsg>(.*?)</errMsg>");
    private static final Pattern XML_REASON_CODE_PATTERN = Pattern.compile("<returnReasonCode>(.*?)</returnReasonCode>");

    private final WebClient webClient;
    private final String serviceKey;
    private final ObjectMapper objectMapper;

    public CongestionApiClient(
            WebClient.Builder builder,
            @Value("${congestion.api.key:${PUBLIC_DATA_SERVICE_KEY:}}") String serviceKey,
            @Value("${congestion.api.base-url:http://apis.data.go.kr/B551011/TatsCnctrRateService}") String baseUrl
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
     * 관광지별 향후 30일 집중률 정보 목록 조회 (/tatsCnctrRatedList)
     */
    public CongestionApiResponse getConcentrationRate(String areaCd, String signguCd, String spotName, int pageNo, int numOfRows) {
        validateServiceKey();
        try {
            String rawResponse = webClient.get()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder.path("/tatsCnctrRatedList")
                                .queryParam("serviceKey", serviceKey)
                                .queryParam("MobileOS", "ETC")
                                .queryParam("MobileApp", "picngo")
                                .queryParam("_type", "json")
                                .queryParam("areaCd", areaCd)
                                .queryParam("signguCd", signguCd)
                                .queryParam("pageNo", pageNo)
                                .queryParam("numOfRows", numOfRows);
                        if (spotName != null && !spotName.isBlank()) {
                            builder.queryParam("tAtsNm", spotName.trim());
                        }
                        URI finalUri = builder.build();
                        log.info("[CongestionApiClient] GET 호출: {}", maskKey(finalUri));
                        return finalUri;
                    })
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(CALL_TIMEOUT)
                    .block();

            return parseAndValidateResponse(rawResponse, "tatsCnctrRatedList (areaCd=" + areaCd + ", signguCd=" + signguCd + ", spotName=" + spotName + ")");
        } catch (WebClientResponseException e) {
            log.error("[CongestionApiClient] tatsCnctrRatedList HTTP 오류 (areaCd={}, signguCd={}, spot={}): {} - {}",
                    areaCd, signguCd, spotName, e.getStatusCode(), e.getResponseBodyAsString());
            return new CongestionApiResponse(new CongestionApiResponse.Response(
                    new CongestionApiResponse.Header(String.valueOf(e.getStatusCode().value()), e.getMessage()),
                    new CongestionApiResponse.Body(new CongestionApiResponse.Items(Collections.emptyList()), 0, pageNo, 0)
            ));
        } catch (Exception e) {
            log.warn("[CongestionApiClient] tatsCnctrRatedList 호출 실패 (areaCd={}, signguCd={}, spot={}): {}",
                    areaCd, signguCd, spotName, e.getMessage());
            return new CongestionApiResponse(new CongestionApiResponse.Response(
                    new CongestionApiResponse.Header("ERROR", e.getMessage()),
                    new CongestionApiResponse.Body(new CongestionApiResponse.Items(Collections.emptyList()), 0, pageNo, 0)
            ));
        }
    }

    private CongestionApiResponse parseAndValidateResponse(String rawResponse, String context) {
        if (rawResponse == null || rawResponse.isBlank()) {
            log.warn("[CongestionApiClient] {} 응답이 비어있습니다.", context);
            return new CongestionApiResponse(new CongestionApiResponse.Response(
                    new CongestionApiResponse.Header("EMPTY", "Empty response"),
                    new CongestionApiResponse.Body(new CongestionApiResponse.Items(Collections.emptyList()), 0, 0, 0)
            ));
        }

        String trimmed = rawResponse.trim();
        if (trimmed.startsWith("<")) {
            String authMsg = extractTag(trimmed, XML_AUTH_MSG_PATTERN);
            String errMsg = extractTag(trimmed, XML_ERR_MSG_PATTERN);
            String reasonCode = extractTag(trimmed, XML_REASON_CODE_PATTERN);
            log.warn("[CongestionApiClient] {} XML 오류 응답 수신: reasonCode={}, errMsg={}, authMsg={}",
                    context, reasonCode, errMsg, authMsg);
            return new CongestionApiResponse(new CongestionApiResponse.Response(
                    new CongestionApiResponse.Header(reasonCode != null ? reasonCode : "XML_ERROR", errMsg != null ? errMsg : authMsg),
                    new CongestionApiResponse.Body(new CongestionApiResponse.Items(Collections.emptyList()), 0, 0, 0)
            ));
        }

        try {
            CongestionApiResponse response = objectMapper.readValue(rawResponse, CongestionApiResponse.class);
            if (response == null || response.response() == null) {
                return new CongestionApiResponse(new CongestionApiResponse.Response(
                        new CongestionApiResponse.Header("EMPTY_BODY", "No response body"),
                        new CongestionApiResponse.Body(new CongestionApiResponse.Items(Collections.emptyList()), 0, 0, 0)
                ));
            }
            return response;
        } catch (Exception e) {
            log.error("[CongestionApiClient] {} JSON 파싱 실패: {} (raw: {})", context, e.getMessage(), rawResponse);
            return new CongestionApiResponse(new CongestionApiResponse.Response(
                    new CongestionApiResponse.Header("PARSE_ERROR", e.getMessage()),
                    new CongestionApiResponse.Body(new CongestionApiResponse.Items(Collections.emptyList()), 0, 0, 0)
            ));
        }
    }

    private void validateServiceKey() {
        if (serviceKey == null || serviceKey.isBlank()) {
            throw new IllegalStateException("공공데이터포털 집중률 서비스키(CONGESTION_SERVICE_KEY 또는 PUBLIC_DATA_SERVICE_KEY)가 설정되지 않았습니다.");
        }
    }

    private String extractTag(String xml, Pattern pattern) {
        Matcher matcher = pattern.matcher(xml);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private String maskKey(URI uri) {
        if (uri == null) return "null";
        return uri.toString().replaceAll("(?i)(serviceKey=)[^&]+", "$1***");
    }
}
