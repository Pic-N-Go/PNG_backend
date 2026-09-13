package com.project.picngo.admin.audit.domain;

import lombok.Getter;

@Getter
public enum AdminActionType {
    ROLE_UPDATE("회원 권한 변경"),
    INQUIRY_ANSWER("1:1 문의 답변 등록/수정"),
    EMBEDDING_RECALCULATE("스팟 임베딩 개별 재계산"),
    EMBEDDING_BACKFILL("스팟 임베딩 일괄 백필"),
    TOUR_API_SYNC("한국관광공사 TourAPI 동기화"),
    CONTEST_CREATE("콘테스트 회차 개설"),
    CONTEST_UPDATE("콘테스트 정보/일정 수정"),
    CONTEST_ENTRY_DELETE("콘테스트 출품작 강제 삭제"),
    CONTEST_NOTIFICATION_SEND("콘테스트 시작 알림 수동 발송"),
    CONTEST_RESULT_PUBLISH("콘테스트 결과 강제 발표 및 조기 마감"),
    CONTEST_RESULT_NOTIFICATION_SEND("콘테스트 결과 알림 수동 발송");

    private final String description;

    AdminActionType(String description) {
        this.description = description;
    }
}
