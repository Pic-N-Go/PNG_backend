package com.project.picngo.contest.domain;

public enum ContestPhase {
    UPCOMING,   // 개설됐지만 아직 출품 시작 전
    SUBMITTING, // 출품 기간
    VOTING,     // 투표 기간
    RESULT,     // 결과 발표 기간
    ENDED       // 종료된 콘테스트
}
