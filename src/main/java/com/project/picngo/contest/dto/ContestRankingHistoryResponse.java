package com.project.picngo.contest.dto;

import com.project.picngo.contest.domain.ContestPhase;

import java.time.LocalDate;
import java.util.List;

public record ContestRankingHistoryResponse(
        Long contestId,
        ContestPhase phase,
        List<Snapshot> snapshots
) {

    public record Snapshot(
            LocalDate snapshotDate,
            boolean completed,
            List<Ranking> rankings
    ) {
    }

    public record Ranking(
            int rank,
            Long entryId,
            String photoUrl,
            String authorNickname,
            String spotName,
            int voteCount
    ) {
    }
}
