package com.project.picngo.contest.dto;

public record ContestVoteResponse(
        Long entryId,
        boolean voted,
        int voteLimit,
        long usedVoteCount,
        long remainingVoteCount
) {
}
