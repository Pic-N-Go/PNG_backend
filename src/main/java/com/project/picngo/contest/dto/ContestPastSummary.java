package com.project.picngo.contest.dto;

public record ContestPastSummary(
        Long contestId,
        long entryCount,
        long participantCount,
        long totalVoteCount
) {
}
