package com.project.picngo.contest.dto;

import java.util.List;

public record ContestMyHistoryResponse(
        long totalEntryCount,
        Integer bestRank,
        long totalVoteCount,
        List<HistoryItem> items
) {

    public record HistoryItem(
            Long contestId,
            String title,
            String thumbnailUrl,
            Integer myRank,
            Integer voteCount,
            String status
    ) {
    }
}
