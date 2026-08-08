package com.project.picngo.spot.dto;

import com.project.picngo.spot.domain.enums.AccessType;

/**
 * 프론트 표시용 길안내 상태.
 * 내부 상태인 AccessType을 그대로 노출하지 않는다.
 * UNKNOWN(미확인)과 ROAD_ACCESSIBLE(확인됨)은 화면상 동작이 같아 DIRECT로 합친다.
 */
public enum NavigationStatus {

    /** 원본 좌표로 바로 안내 가능 */
    DIRECT,

    /** 주차장/입구 좌표로 보정됨 */
    CORRECTED,

    /** 보정 탐색까지 실패. 차량 접근이 어려운 스팟 */
    UNREACHABLE;

    public static NavigationStatus from(AccessType accessType) {
        if (accessType == AccessType.NEEDS_ENTRANCE) {
            return CORRECTED;
        }
        if (accessType == AccessType.RESOLVE_FAILED) {
            return UNREACHABLE;
        }
        return DIRECT;
    }
}
