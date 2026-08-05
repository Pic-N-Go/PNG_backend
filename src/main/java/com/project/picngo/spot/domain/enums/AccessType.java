package com.project.picngo.spot.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AccessType {
    UNKNOWN("진입 속성 미확인"),
    ROAD_ACCESSIBLE("도로 직결 접근 가능"),
    NEEDS_ENTRANCE("비도로 스팟 (주차장/입구 보정 필요)"),
    RESOLVE_FAILED("주차장/입구 보정 탐색 실패");

    private final String description;
}
