package com.project.picngo.contest.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ContestMyVoteResponse(
        Long contestId,
        int voteLimit,
        long usedVoteCount,
        long remainingVoteCount,
        List<VotedEntry> votedEntries
) {

    public record VotedEntry(
            Long entryId,
            String photoUrl,
            String authorNickname,
            String spotName,
            LocalDateTime votedAt
    ) {
    }
}
