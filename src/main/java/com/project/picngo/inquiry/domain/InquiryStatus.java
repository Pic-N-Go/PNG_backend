package com.project.picngo.inquiry.domain;

import lombok.Getter;

@Getter
public enum InquiryStatus {
    PENDING("답변 대기"),
    ANSWERED("답변 완료"),
    RESOLVED("해결됨");

    private final String description;

    InquiryStatus(String description) {
        this.description = description;
    }
}
