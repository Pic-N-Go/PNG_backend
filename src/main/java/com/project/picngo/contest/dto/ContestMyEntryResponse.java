package com.project.picngo.contest.dto;

import com.project.picngo.contest.domain.ContestPhase;

import java.util.List;

public record ContestMyEntryResponse(
        Long contestId,
        String title,
        ContestPhase phase,
        int myEntryCount,
        int maxEntriesPerUser,
        int remainingEntryCount,
        List<ContestEntryResponse> entries
) {
}
