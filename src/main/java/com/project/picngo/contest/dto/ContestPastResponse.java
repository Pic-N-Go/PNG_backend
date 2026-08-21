package com.project.picngo.contest.dto;

public record ContestPastResponse(
        Long contestId,
        String title,
        String themeImageUrl,
        int entryCount,
        long participantCount,
        int totalVoteCount,
        Integer myRank,
        String winnerNickname
) {
}
