package com.project.picngo.spot.domain;

import com.project.picngo.common.domain.SpotCategory;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * TourAPI 스팟에 사진테마 카테고리를 부여한다.
 * - 장소형: cat3 코드로 정확 매핑
 * - 장면형: name/overview 키워드 매칭 (오탐 가능한 상한선)
 * 아무것도 안 붙으면 ETC.
 */
public final class SpotCategoryTagger {

    private SpotCategoryTagger() {}

    // cat3(TourAPI 소분류) → 장소형 테마. 관광공사 규격 기반, 정확.
    private static final Map<String, SpotCategory> CAT3 = Map.ofEntries(
            Map.entry("A02020700", SpotCategory.PARK),      // 공원
            // A02020600(실내 테마파크/아쿠아리움)은 PARK에서 제외 — 실제 spot 테이블 데이터 라벨 기준.
            // 야외 공원 필터에 아쿠아리움이 섞여 나온다.
            Map.entry("A01011200", SpotCategory.BEACH),     // 해수욕장
            Map.entry("A01010400", SpotCategory.MOUNTAIN),  // 산
            Map.entry("A01020200", SpotCategory.MOUNTAIN),  // 기암괴석
            Map.entry("A02010400", SpotCategory.HANOK),     // 고택
            Map.entry("A02010600", SpotCategory.HANOK),     // 민속마을
            Map.entry("A02010100", SpotCategory.HANOK),     // 고궁
            Map.entry("A01010600", SpotCategory.FOREST),    // 자연휴양림
            Map.entry("A01010700", SpotCategory.FOREST),    // 수목원
            Map.entry("A01010500", SpotCategory.FOREST),    // 자연생태관광지
            Map.entry("A02010700", SpotCategory.HERITAGE),  // 유적지
            Map.entry("A02010800", SpotCategory.HERITAGE),  // 사찰
            Map.entry("A02010200", SpotCategory.HERITAGE),  // 성
            Map.entry("A02010900", SpotCategory.HERITAGE)   // 종교성지
    );

    // 키워드(name/overview 포함 시) → 장면형 테마.
    private record KeywordRule(SpotCategory theme, List<String> keywords) {}

    private static final List<KeywordRule> KEYWORD_RULES = List.of(
            new KeywordRule(SpotCategory.NIGHT_VIEW, List.of("야경", "전망대")),
            new KeywordRule(SpotCategory.CAFE, List.of("카페", "커피")),
            new KeywordRule(SpotCategory.SUNRISE_SUNSET, List.of("일출", "일몰", "노을")),
            new KeywordRule(SpotCategory.FLOWER, List.of("벚꽃", "단풍", "유채")),
            new KeywordRule(SpotCategory.FESTIVAL, List.of("축제", "페스티벌")),
            new KeywordRule(SpotCategory.MILKY_WAY, List.of("은하수")),
            // ponytail: 시내/도심/도시는 "시내를 내려다보는 산·절"을 오태깅해서 제외
            new KeywordRule(SpotCategory.CITY, List.of("골목", "번화가", "야시장", "로데오"))
    );

    public static Set<SpotCategory> tag(String cat3, String name, String overview) {
        Set<SpotCategory> result = EnumSet.noneOf(SpotCategory.class);

        if (cat3 != null) {
            SpotCategory place = CAT3.get(cat3);
            if (place != null) {
                result.add(place);
            }
        }

        String text = nullToEmpty(name) + " " + nullToEmpty(overview);
        for (KeywordRule rule : KEYWORD_RULES) {
            for (String keyword : rule.keywords()) {
                if (text.contains(keyword)) {
                    result.add(rule.theme());
                    break;
                }
            }
        }

        if (result.isEmpty()) {
            result.add(SpotCategory.ETC);
        }
        return result;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
