package com.project.picngo.contest.dto;

import java.util.List;

public record ContestResultResponse(
        Long contestId,
        String title,
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
