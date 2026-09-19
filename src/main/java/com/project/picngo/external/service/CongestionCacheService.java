package com.project.picngo.external.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.picngo.external.CongestionApiClient;
import com.project.picngo.external.dto.CongestionApiResponse;
import com.project.picngo.external.dto.CongestionApiResponse.CongestionItem;
import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.service.AdministrativeCodeResolver;
import com.project.picngo.spot.service.AdministrativeCodeResolver.AdministrativeCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CongestionCacheService {

    private static final long TTL_HOURS = 24;
    private static final long EMPTY_TTL_HOURS = 2;

    private final CongestionApiClient congestionApiClient;
    private final AdministrativeCodeResolver administrativeCodeResolver;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<CongestionItem> getCachedCongestion(Spot spot) {
        if (spot == null || spot.getAddress() == null || spot.getAddress().isBlank()) {
            return Collections.emptyList();
        }

        String cacheKey = "congestion:spot:" + spot.getId();
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                log.debug("[CongestionCacheService] Redis 캐시 히트: {}", cacheKey);
                return objectMapper.readValue(cached, new TypeReference<List<CongestionItem>>() {});
            }
        } catch (Exception e) {
            log.warn("[CongestionCacheService] Redis 캐시 조회 실패: {}", e.getMessage());
        }

        Optional<AdministrativeCode> codeOpt = administrativeCodeResolver.resolve(spot.getAddress());
        if (codeOpt.isEmpty()) {
            log.debug("[CongestionCacheService] 행정구역 코드 조회 불가 (address={})", spot.getAddress());
            return Collections.emptyList();
        }

        AdministrativeCode code = codeOpt.get();
        List<CongestionItem> items = fetchFromApi(code.areaCd(), code.signguCd(), spot.getName(), 30);

        // 1. 스팟 이름에 괄호나 부가 설명이 붙어있는 경우(예: '간현관광지 (소금산)') 정제 후 재시도
        if (items.isEmpty() && spot.getName() != null && spot.getName().contains("(")) {
            String simplified = spot.getName().replaceAll("\\(.*?\\)", "").trim();
            if (!simplified.isEmpty() && !simplified.equals(spot.getName())) {
                log.debug("[CongestionCacheService] 정제된 명칭으로 재시도: '{}' -> '{}'", spot.getName(), simplified);
                items = fetchFromApi(code.areaCd(), code.signguCd(), simplified, 30);
            }
        }

        // 2. 정확 일치 실패 시: 해당 시군구의 관광공사 명소 목록에서 상위 명소 스마트 매칭
        // (예: '남산 팔각정' -> '남산공원(서울)', '덕수궁 돌담길' -> '덕수궁')
        if (items.isEmpty()) {
            items = matchFromRegionAttractions(code.areaCd(), code.signguCd(), spot.getName());
        }

        // Redis 저장
        try {
            if (!items.isEmpty()) {
                redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(items), TTL_HOURS, TimeUnit.HOURS);
            } else {
                // 빈 결과도 짧게 캐싱하여 API 호출 폭주 방지
                redisTemplate.opsForValue().set(cacheKey, "[]", EMPTY_TTL_HOURS, TimeUnit.HOURS);
            }
        } catch (Exception e) {
            log.warn("[CongestionCacheService] Redis 캐시 저장 실패: {}", e.getMessage());
        }

        return items;
    }

    private List<CongestionItem> matchFromRegionAttractions(String areaCd, String signguCd, String spotName) {
        if (spotName == null || spotName.isBlank()) {
            return Collections.emptyList();
        }

        List<CongestionItem> regionItems = getRegionAllItems(areaCd, signguCd);
        if (regionItems.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> attractionNames = regionItems.stream()
                .map(CongestionItem::tAtsNm)
                .filter(name -> name != null && !name.isBlank())
                .collect(Collectors.toSet());

        String bestMatch = findBestAttraction(spotName, attractionNames);
        if (bestMatch == null) {
            return Collections.emptyList();
        }

        log.info("[CongestionCacheService] 스팟 '{}' -> 공사 관광지 '{}' 스마트 매칭 성공", spotName, bestMatch);

        return regionItems.stream()
                .filter(item -> bestMatch.equals(item.tAtsNm()))
                .limit(30)
                .toList();
    }

    private List<CongestionItem> getRegionAllItems(String areaCd, String signguCd) {
        String regionCacheKey = "congestion:region:" + areaCd + ":" + signguCd;
        try {
            String cached = redisTemplate.opsForValue().get(regionCacheKey);
            if (cached != null) {
                return objectMapper.readValue(cached, new TypeReference<List<CongestionItem>>() {});
            }
        } catch (Exception e) {
            log.warn("[CongestionCacheService] 지역 캐시 조회 실패: {}", e.getMessage());
        }

        List<CongestionItem> items = fetchFromApi(areaCd, signguCd, null, 1000);
        try {
            if (!items.isEmpty()) {
                redisTemplate.opsForValue().set(regionCacheKey, objectMapper.writeValueAsString(items), TTL_HOURS, TimeUnit.HOURS);
            } else {
                redisTemplate.opsForValue().set(regionCacheKey, "[]", EMPTY_TTL_HOURS, TimeUnit.HOURS);
            }
        } catch (Exception e) {
            log.warn("[CongestionCacheService] 지역 캐시 저장 실패: {}", e.getMessage());
        }
        return items;
    }

    private String cleanName(String name) {
        if (name == null) return "";
        return name.replaceAll("\\(.*?\\)", "")
                .replaceAll("\\[.*?\\]", "")
                .replaceAll("\\s+", "")
                .trim();
    }

    private String findBestAttraction(String spotName, Set<String> attractionNames) {
        String cleanSpot = cleanName(spotName);
        if (cleanSpot.isEmpty()) return null;

        // 1. 공백/특수문자 제거 후 정확 일치
        for (String a : attractionNames) {
            if (cleanName(a).equalsIgnoreCase(cleanSpot)) {
                return a;
            }
        }

        // 2. 스팟명이 관광지명을 포함하는 경우 (예: "덕수궁 돌담길" -> "덕수궁", "경복궁 향원정" -> "경복궁")
        String bestContained = null;
        for (String a : attractionNames) {
            String ca = cleanName(a);
            if (ca.length() >= 2 && cleanSpot.contains(ca)) {
                if (bestContained == null || ca.length() > cleanName(bestContained).length()) {
                    bestContained = a;
                }
            }
        }
        if (bestContained != null) {
            return bestContained;
        }

        // 3. 서브 스팟 접미어 제거 후 루트 키워드 매칭
        // 예: "남산 팔각정" / "남산팔각정" -> "남산" -> "남산공원(서울)"
        String[] suffixes = {
                "팔각정", "전망대", "출렁다리", "스카이워크", "봉수대", "포토존", "돌담길", "둘레길",
                "산책로", "정상", "입구", "매표소", "주차장", "터", "정자", "전망쉼터"
        };

        String root = cleanSpot;
        for (String suffix : suffixes) {
            if (root.endsWith(suffix)) {
                root = root.substring(0, root.length() - suffix.length());
                break;
            }
        }

        if (root.length() >= 2) {
            List<String> candidates = new ArrayList<>();
            for (String a : attractionNames) {
                if (cleanName(a).contains(root)) {
                    candidates.add(a);
                }
            }

            if (!candidates.isEmpty()) {
                // 공원 > 타워 > 산 > 궁 등 자연/경관/명소 우선 선택
                for (String c : candidates) {
                    if (c.contains("공원")) return c;
                }
                for (String c : candidates) {
                    if (c.contains("타워") || c.contains("산") || c.contains("궁")) return c;
                }
                return candidates.get(0);
            }
        }

        return null;
    }

    private List<CongestionItem> fetchFromApi(String areaCd, String signguCd, String name, int numOfRows) {
        try {
            CongestionApiResponse response = congestionApiClient.getConcentrationRate(areaCd, signguCd, name, 1, numOfRows);
            if (response != null && response.response() != null && response.response().body() != null
                    && response.response().body().items() != null
                    && response.response().body().items().item() != null) {
                return response.response().body().items().item();
            }
        } catch (Exception e) {
            log.warn("[CongestionCacheService] API 조회 실패 (areaCd={}, signguCd={}, name={}): {}",
                    areaCd, signguCd, name, e.getMessage());
        }
        return Collections.emptyList();
    }
}
