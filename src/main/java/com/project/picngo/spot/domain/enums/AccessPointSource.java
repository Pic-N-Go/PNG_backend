package com.project.picngo.spot.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AccessPointSource {
    KAKAO_LOCAL("카카오 지도 키워드 검색"),
    MANUAL("관리자 수동 지정"),
    USER_FEEDBACK("유저 피드백 수집");

    private final String description;
}
