package com.project.picngo.spot.domain;

import com.project.picngo.common.domain.SpotCategory;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * TourAPI 스팟에 사진테마 카테고리를 부여한다.
 * 1. 장소형/테마형: TourAPI 소분류 코드(lclsSystm3 및 구버전 cat3)로 정확 매핑
 * 2. 장면형: name/overview 키워드 매칭 보완
 * 3. 아무것도 안 붙으면 ETC.
 */
public final class SpotCategoryTagger {

    private SpotCategoryTagger() {}

    private static final Map<String, SpotCategory> CATEGORY_MAP = new HashMap<>();

    static {
        // ==========================================
        // [1] TourAPI 4.4 신규 소분류 코드 (lclsSystm3)
        // ==========================================
        // PARK (도시공원)
        CATEGORY_MAP.put("VE030100", SpotCategory.PARK); // 시민공원
        CATEGORY_MAP.put("VE030200", SpotCategory.PARK); // 소공원
        CATEGORY_MAP.put("VE030300", SpotCategory.PARK); // 어린이공원
        CATEGORY_MAP.put("VE030400", SpotCategory.PARK); // 근린공원
        CATEGORY_MAP.put("VE030500", SpotCategory.PARK); // 주제공원

        // BEACH (해안/바다/해변)
        CATEGORY_MAP.put("NA020900", SpotCategory.BEACH); // 해변/해수욕장
        CATEGORY_MAP.put("NA020800", SpotCategory.BEACH); // 해안절경
        CATEGORY_MAP.put("NA020700", SpotCategory.BEACH); // 항구/포구
        CATEGORY_MAP.put("NA020500", SpotCategory.BEACH); // 섬
        CATEGORY_MAP.put("VE010800", SpotCategory.BEACH); // 등대

        // MOUNTAIN (산/자연경관)
        CATEGORY_MAP.put("NA010100", SpotCategory.MOUNTAIN); // 산, 고개, 오름, 봉우리
        CATEGORY_MAP.put("NA010300", SpotCategory.MOUNTAIN); // 폭포
        CATEGORY_MAP.put("NA010400", SpotCategory.MOUNTAIN); // 계곡
        CATEGORY_MAP.put("NA010500", SpotCategory.MOUNTAIN); // 약수터
        CATEGORY_MAP.put("NA030100", SpotCategory.MOUNTAIN); // 동굴
        CATEGORY_MAP.put("NA030300", SpotCategory.MOUNTAIN); // 기암괴석
        CATEGORY_MAP.put("NA040100", SpotCategory.MOUNTAIN); // 국립공원
        CATEGORY_MAP.put("NA040200", SpotCategory.MOUNTAIN); // 도립공원
        CATEGORY_MAP.put("NA040300", SpotCategory.MOUNTAIN); // 군립공원
        CATEGORY_MAP.put("NA040400", SpotCategory.MOUNTAIN); // 지질공원

        // FOREST (숲/수목원/생태)
        CATEGORY_MAP.put("NA010200", SpotCategory.FOREST); // 숲
        CATEGORY_MAP.put("NA040600", SpotCategory.FOREST); // 자연휴양림
        CATEGORY_MAP.put("NA040700", SpotCategory.FOREST); // 수목원ㆍ정원
        CATEGORY_MAP.put("NA040500", SpotCategory.FOREST); // 생태관광지
        CATEGORY_MAP.put("NA030400", SpotCategory.FOREST); // 생태습지
        CATEGORY_MAP.put("NA030500", SpotCategory.FOREST); // 기타자연생태
        CATEGORY_MAP.put("VE040300", SpotCategory.FOREST); // 둘레길

        // HANOK (고궁/한옥/민속마을)
        CATEGORY_MAP.put("HS010100", SpotCategory.HANOK); // 고궁
        CATEGORY_MAP.put("HS010400", SpotCategory.HANOK); // 고택
        CATEGORY_MAP.put("HS010500", SpotCategory.HANOK); // 생가
        CATEGORY_MAP.put("HS010600", SpotCategory.HANOK); // 민속마을
        CATEGORY_MAP.put("AC030200", SpotCategory.HANOK); // 한옥스테이

        // HERITAGE (역사유적/사찰/문화시설)
        CATEGORY_MAP.put("HS010200", SpotCategory.HERITAGE); // 성ㆍ산성ㆍ성곽
        CATEGORY_MAP.put("HS010300", SpotCategory.HERITAGE); // 문
        CATEGORY_MAP.put("HS010700", SpotCategory.HERITAGE); // 사적지
        CATEGORY_MAP.put("HS010800", SpotCategory.HERITAGE); // 고분, 능
        CATEGORY_MAP.put("HS010900", SpotCategory.HERITAGE); // 사당
        CATEGORY_MAP.put("HS011000", SpotCategory.HERITAGE); // 선사유적지
        CATEGORY_MAP.put("HS011100", SpotCategory.HERITAGE); // 근대건축물
        CATEGORY_MAP.put("HS011200", SpotCategory.HERITAGE); // 기타역사유적지
        CATEGORY_MAP.put("HS020100", SpotCategory.HERITAGE); // 탑ㆍ비석ㆍ기념탑
        CATEGORY_MAP.put("HS020200", SpotCategory.HERITAGE); // 선사유물
        CATEGORY_MAP.put("HS020300", SpotCategory.HERITAGE); // 불상
        CATEGORY_MAP.put("HS020400", SpotCategory.HERITAGE); // 기타역사유물
        CATEGORY_MAP.put("HS030100", SpotCategory.HERITAGE); // 불교 (사찰)
        CATEGORY_MAP.put("HS030200", SpotCategory.HERITAGE); // 기독교 성지
        CATEGORY_MAP.put("HS030400", SpotCategory.HERITAGE); // 기타 종교성지
        CATEGORY_MAP.put("HS040100", SpotCategory.HERITAGE); // 안보유적지
        CATEGORY_MAP.put("EX040100", SpotCategory.HERITAGE); // 템플스테이
        CATEGORY_MAP.put("EX040200", SpotCategory.HERITAGE); // 사찰문화체험
        CATEGORY_MAP.put("VE070100", SpotCategory.HERITAGE); // 박물관
        CATEGORY_MAP.put("VE070200", SpotCategory.HERITAGE); // 기념관
        CATEGORY_MAP.put("VE070300", SpotCategory.HERITAGE); // 전시관
        CATEGORY_MAP.put("VE070600", SpotCategory.HERITAGE); // 미술관/화랑

        // CAFE (카페/찻집)
        CATEGORY_MAP.put("FD050100", SpotCategory.CAFE); // 카페
        CATEGORY_MAP.put("FD050200", SpotCategory.CAFE); // 찻집
        CATEGORY_MAP.put("FD050300", SpotCategory.CAFE); // 기타음료점

        // CITY (골목/거리/전통시장)
        CATEGORY_MAP.put("VE040100", SpotCategory.CITY); // 골목길, 문화거리
        CATEGORY_MAP.put("VE040200", SpotCategory.CITY); // 마을관광지
        CATEGORY_MAP.put("SH060100", SpotCategory.CITY); // 비상설시장
        CATEGORY_MAP.put("SH060200", SpotCategory.CITY); // 상설시장

        // NIGHT_VIEW (타워/전망대)
        CATEGORY_MAP.put("VE010200", SpotCategory.NIGHT_VIEW); // 타워 / 전망대

        // FESTIVAL (축제/행사)
        CATEGORY_MAP.put("EV010100", SpotCategory.FESTIVAL); // 문화관광축제
        CATEGORY_MAP.put("EV010200", SpotCategory.FESTIVAL); // 문화예술축제
        CATEGORY_MAP.put("EV010300", SpotCategory.FESTIVAL); // 지역특산물축제
        CATEGORY_MAP.put("EV010400", SpotCategory.FESTIVAL); // 전통역사축제
        CATEGORY_MAP.put("EV010500", SpotCategory.FESTIVAL); // 생태자연축제
        CATEGORY_MAP.put("EV010600", SpotCategory.FESTIVAL); // 기타축제
        CATEGORY_MAP.put("EV030100", SpotCategory.FESTIVAL); // 전시회
        CATEGORY_MAP.put("EV030200", SpotCategory.FESTIVAL); // 박람회

        // ==========================================
        // [2] 구버전 cat3 호환용 매핑
        // ==========================================
        CATEGORY_MAP.put("A02020700", SpotCategory.PARK);
        CATEGORY_MAP.put("A01011200", SpotCategory.BEACH);
        CATEGORY_MAP.put("A01010400", SpotCategory.MOUNTAIN);
        CATEGORY_MAP.put("A01020200", SpotCategory.MOUNTAIN);
        CATEGORY_MAP.put("A02010400", SpotCategory.HANOK);
        CATEGORY_MAP.put("A02010600", SpotCategory.HANOK);
        CATEGORY_MAP.put("A02010100", SpotCategory.HANOK);
        CATEGORY_MAP.put("A01010600", SpotCategory.FOREST);
        CATEGORY_MAP.put("A01010700", SpotCategory.FOREST);
        CATEGORY_MAP.put("A01010500", SpotCategory.FOREST);
        CATEGORY_MAP.put("A02010700", SpotCategory.HERITAGE);
        CATEGORY_MAP.put("A02010800", SpotCategory.HERITAGE);
        CATEGORY_MAP.put("A02010200", SpotCategory.HERITAGE);
        CATEGORY_MAP.put("A02010900", SpotCategory.HERITAGE);
    }

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

    public static Set<SpotCategory> tag(String categoryCode, String name, String overview) {
        Set<SpotCategory> result = EnumSet.noneOf(SpotCategory.class);

        if (categoryCode != null && !categoryCode.isBlank()) {
            SpotCategory place = CATEGORY_MAP.get(categoryCode.trim());
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

    /**
     * 사용자가 입력한 검색어가 서비스 테마/카테고리명인지 판별한다.
     * '바다', '카페', '야경', '일몰', '축제' 등 테마 키워드 검색 시 1단계에서 해당 카테고리 스팟을 즉시 매칭하기 위함.
     */
    public static SpotCategory matchCategoryByKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String k = keyword.trim().toLowerCase();
        return switch (k) {
            case "바다", "해변", "해수욕장", "해안", "바닷가", "오션", "백사장" -> SpotCategory.BEACH;
            case "카페", "커피", "디저트", "베이커리", "찻집" -> SpotCategory.CAFE;
            case "야경", "밤풍경", "야간경관", "야간" -> SpotCategory.NIGHT_VIEW;
            case "일출", "일몰", "노을", "해돋이", "해넘이" -> SpotCategory.SUNRISE_SUNSET;
            case "공원", "산책", "피크닉", "유원지" -> SpotCategory.PARK;
            case "산", "등산", "계곡", "봉우리", "트래킹" -> SpotCategory.MOUNTAIN;
            case "숲", "수목원", "휴양림", "자연휴양림", "삼림욕" -> SpotCategory.FOREST;
            case "한옥", "한옥마을", "고택" -> SpotCategory.HANOK;
            case "역사", "유적지", "문화재", "박물관", "미술관", "전시관" -> SpotCategory.HERITAGE;
            case "축제", "행사", "페스티벌", "마켓" -> SpotCategory.FESTIVAL;
            case "꽃", "벚꽃", "단풍", "억새", "튤립" -> SpotCategory.FLOWER;
            case "은하수", "별", "별자리", "천문대" -> SpotCategory.MILKY_WAY;
            case "도심", "벽화마을", "거리", "핫플", "핫플레이스" -> SpotCategory.CITY;
            default -> null;
        };
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
