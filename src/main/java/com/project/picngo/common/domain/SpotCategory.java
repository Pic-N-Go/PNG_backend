package com.project.picngo.common.domain;

// User, Spot 도메인 등 공통으로 사용하는 SpotCategory Enum 값
// 스팟에 값을 부여하는 규칙은 SpotCategoryTagger 참고.
public enum SpotCategory {

    // 장소형 — TourAPI cat3 코드로 정확 매핑. 신뢰 가능.
    PARK,
    BEACH,
    MOUNTAIN,
    HANOK,
    FOREST,
    HERITAGE,

    // 장면형 — name/overview 키워드로 추출. TourAPI에 소스가 없어 오탐 가능.
    CAFE,
    CITY,
    NIGHT_VIEW,
    FESTIVAL,
    FLOWER,
    SUNRISE_SUNSET,
    MILKY_WAY,

    ETC;

    public String getKeywords() {
        return switch (this) {
            case BEACH -> "바다, 해변, 해수욕장, 해안, 바닷가";
            case PARK -> "공원, 산책, 유원지, 힐링";
            case MOUNTAIN -> "산, 등산, 계곡, 봉우리, 트래킹";
            case HANOK -> "한옥, 전통가옥, 고택, 민속마을";
            case FOREST -> "숲, 휴양림, 수목원, 자연숲, 피톤치드";
            case HERITAGE -> "역사, 유적지, 문화재, 전시관, 박물관, 미술관";
            case CAFE -> "카페, 디저트, 찻집, 커피, 베이커리";
            case CITY -> "도심, 거리, 벽화마을, 명소, 핫플레이스";
            case NIGHT_VIEW -> "야경, 밤풍경, 야간경관, 드라이브";
            case FESTIVAL -> "축제, 행사, 페스티벌, 마켓, 공연";
            case FLOWER -> "꽃, 벚꽃, 억새, 튤립, 단풍, 식물원";
            case SUNRISE_SUNSET -> "일출, 일몰, 노을, 해넘이, 해돋이";
            case MILKY_WAY -> "은하수, 별자리, 천문대, 별보기";
            case ETC -> "명소";
        };
    }
}