package com.project.picngo.contest.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ContestResultResponse(
        Long contestId,
        String title,
        // 결과 화면 상단의 기간 표기용 — 이것 없이는 /contests/{id}를 한 번 더 불러야 한다
        LocalDateTime submitStartAt,
        LocalDateTime voteEndAt,
        int entryCount,
        long participantCount,
        int totalVoteCount,
        ResultEntry winner,
        ResultEntry myResult,
        List<ResultEntry> rankings
) {

    public record ResultEntry(
            int rank,
            Long entryId,
            String photoUrl,
            String authorNickname,
            String caption,
            Long spotId,
            String spotName,
            int voteCount
    ) {
    }
}
