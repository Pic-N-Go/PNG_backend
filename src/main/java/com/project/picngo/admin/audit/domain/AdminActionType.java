package com.project.picngo.admin.audit.domain;

import lombok.Getter;

@Getter
public enum AdminActionType {
    ROLE_UPDATE("회원 권한 변경"),
    INQUIRY_ANSWER("1:1 문의 답변 등록/수정"),
    EMBEDDING_RECALCULATE("스팟 임베딩 개별 재계산"),
    EMBEDDING_BACKFILL("스팟 임베딩 일괄 백필"),
    TOUR_API_SYNC("한국관광공사 TourAPI 동기화");

    private final String description;

    AdminActionType(String description) {
        this.description = description;
    }
}
