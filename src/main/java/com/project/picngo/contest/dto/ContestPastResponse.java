package com.project.picngo.contest.dto;

import java.time.LocalDateTime;

public record ContestPastResponse(
        Long contestId,
        String title,
        String themeImageUrl,
        // 회차를 "6월"처럼 부르려면 클라이언트가 title을 파싱하지 않고 날짜에서 월을 뽑아야 한다
        LocalDateTime submitStartAt,
        // 결과 발표 시각. 지난 회차 수상 배너를 발표 후 한 달 동안만 띄우는 판단에 쓴다
        LocalDateTime resultOpenAt,
        int entryCount,
        long participantCount,
        int totalVoteCount,
        Integer myRank,
        String winnerNickname,
        // 목록 카드 썸네일 = 우승작 사진
        String winnerPhotoUrl
) {
}
