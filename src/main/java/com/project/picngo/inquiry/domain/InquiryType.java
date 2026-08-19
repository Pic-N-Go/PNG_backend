package com.project.picngo.inquiry.domain;

import lombok.Getter;

@Getter
public enum InquiryType {
    FEATURE("기능 문의"),
    BUG("앱 오류 신고"),
    ACCOUNT("계정/로그인"),
    SPOT("스팟 정보 제보"),
    OTHER("기타");

    private final String description;

    InquiryType(String description) {
        this.description = description;
    }
}
