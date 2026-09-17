package com.project.picngo.external;

import com.project.picngo.external.dto.TarRlteTarResponse;
import com.project.picngo.external.dto.TarRlteTarResponse.Item;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.DefaultUriBuilderFactory;

import java.time.Duration;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

/**
 * 한국관광공사 관광지별 연관 관광지 정보 서비스 (TarRlteTarService1) API 클라이언트.
 * 선택한 관광지와 높은 연결성을 가지는 연관 관광지(관광지, 음식, 숙박) 목록을 조회합니다.
 */
@Slf4j
@Component
public class TarRlteTarApiClient {

    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(4);
    private static final DateTimeFormatter YYYYMM = DateTimeFormatter.ofPattern("yyyyMM");

    private final WebClient webClient;
    private final String serviceKey;

    public TarRlteTarApiClient(
            WebClient.Builder builder,
            @Value("${tar-rlte-tar.api.key:${PUBLIC_DATA_SERVICE_KEY:${tour.api.key:}}}") String serviceKey,
            @Value("${tar-rlte-tar.api.base-url:https://apis.data.go.kr/B551011/TarRlteTarService1}") String baseUrl
    ) {
        DefaultUriBuilderFactory factory = new DefaultUriBuilderFactory(baseUrl);
        factory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.NONE);
        this.webClient = builder.uriBuilderFactory(factory).build();
        this.serviceKey = serviceKey != null ? serviceKey.trim() : "";
    }

    public List<Item> searchKeyword(String keyword, String areaCd, String signguCd, String baseYm, int numOfRows) {
        if (serviceKey == null || serviceKey.isBlank() || keyword == null || keyword.isBlank()) {
            return Collections.emptyList();
        }

        String resolvedBaseYm = resolveBaseYm(baseYm);
        try {
            TarRlteTarResponse response = webClient.get()
                    .uri(uriBuilder -> {
                        var b = uriBuilder.path("/searchKeyword1")
                                .queryParam("serviceKey", serviceKey)
                                .queryParam("MobileOS", "ETC")
                                .queryParam("MobileApp", "picngo")
                                .queryParam("_type", "json")
                                .queryParam("baseYm", resolvedBaseYm)
                                .queryParam("keyword", keyword.trim())
                                .queryParam("pageNo", 1)
                                .queryParam("numOfRows", Math.max(1, numOfRows));

                        if (areaCd != null && !areaCd.isBlank()) {
                            b.queryParam("areaCd", areaCd.trim());
                        }
                        if (signguCd != null && !signguCd.isBlank()) {
                            b.queryParam("signguCd", signguCd.trim());
                        }
                        return b.build();
                    })
                    .retrieve()
                    .bodyToMono(TarRlteTarResponse.class)
                    .timeout(CALL_TIMEOUT)
                    .block();

            if (response == null || response.response() == null) {
                log.warn("[TarRlteTarApiClient] 응답 본문이 비어있음: keyword={}, baseYm={}", keyword, resolvedBaseYm);
                return Collections.emptyList();
            }

            var header = response.response().header();
            if (header != null && !"0000".equals(header.resultCode()) && !"00".equals(header.resultCode())) {
                log.warn("[TarRlteTarApiClient] API 오류 응답 [code={}]: msg={}, keyword={}",
                        header.resultCode(), header.resultMsg(), keyword);
                return Collections.emptyList();
            }

            var body = response.response().body();
            if (body == null || body.items() == null || body.items().item() == null) {
                log.info("[TarRlteTarApiClient] 연관 관광지 검색 결과 없음: keyword={}", keyword);
                return Collections.emptyList();
            }

            List<Item> items = body.items().item();
            log.info("[TarRlteTarApiClient] 연관 관광지 조회 성공: keyword={}, count={}", keyword, items.size());
            return items;

        } catch (Exception e) {
            log.warn("[TarRlteTarApiClient] 연관 관광지 API 호출 실패 (비차단 폴백): keyword={}, error={}", keyword, e.getMessage());
            return Collections.emptyList();
        }
    }

    private String resolveBaseYm(String baseYm) {
        if (baseYm != null && !baseYm.isBlank() && baseYm.length() == 6) {
            return baseYm.trim();
        }
        // 공공데이터 특성상 최근 통계 집계는 2개월 전 데이터가 안정적 (월 1회 갱신)
        YearMonth target = YearMonth.now().minusMonths(2);
        return target.format(YYYYMM);
    }
}
