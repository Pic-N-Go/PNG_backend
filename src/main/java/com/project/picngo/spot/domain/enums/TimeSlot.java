package com.project.picngo.spot.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 리뷰 작성 시 사용자가 선택하는 촬영 시간대. 출사알림 알림 조건인 TimeCondition과 무관.
@Getter
@RequiredArgsConstructor
public enum TimeSlot {
    SUNRISE("일출"),
    DAY("낮"),
    SUNSET("일몰"),
    NIGHT("야간");

    private final String label;
}
