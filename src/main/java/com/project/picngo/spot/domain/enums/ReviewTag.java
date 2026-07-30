package com.project.picngo.spot.domain.enums;

/**
 * 리뷰 태그. 사용자 자유 입력은 받지 않는다 —
 * "야경/야경명소/야경사진"이 각각 집계되면 "자주 쓰인 태그" 계산이 무의미해진다.
 *
 * 목록은 리뷰 작성 화면 목업(review-write.html)의 칩 9종과 1:1로 대응한다.
 * 화면 표기용 한글 라벨은 프론트가 갖는다 (API는 아래 이름을 그대로 주고받는다).
 *
 *   LIGHTING 채광맛집 · BEST_SHOT 인생샷 · MOODY 감성사진
 *   NIGHT_VIEW 야경명소 · SUNRISE 일출명소
 *   EASY_PARKING 주차편함 · TRIPOD_NEEDED 삼각대필수 · GOOD_ACCESS 접근성좋음 · GOOD_FOR_SOLO 혼자가기좋음
 *
 * 주의: review_tag.tag가 MySQL 네이티브 ENUM으로 생성되므로 값을 추가하면
 * ddl-auto: update가 컬럼을 넓혀주지 않는다. ALTER TABLE review_tag MODIFY tag ENUM(...) 이 필요하다.
 */
public enum ReviewTag {

    // 사진 결과물
    LIGHTING,
    BEST_SHOT,
    MOODY,

    // 시간대
    NIGHT_VIEW,
    SUNRISE,

    // 방문 조건
    EASY_PARKING,
    TRIPOD_NEEDED,
    GOOD_ACCESS,
    GOOD_FOR_SOLO
}
