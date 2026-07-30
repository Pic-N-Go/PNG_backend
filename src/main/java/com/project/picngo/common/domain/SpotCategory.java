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

    ETC
}