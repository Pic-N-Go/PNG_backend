package com.project.picngo.contest.dto;

import com.project.picngo.contest.domain.Contest;
import com.project.picngo.contest.domain.ContestPhase;

import java.time.LocalDateTime;

public record ContestResponse(
        Long contestId,
        String title,
        String description,
        String themeImageUrl,
        ContestPhase phase,
        LocalDateTime submitStartAt,
        LocalDateTime submitEndAt,
        LocalDateTime voteStartAt,
        LocalDateTime voteEndAt,
        LocalDateTime resultOpenAt,
        int entryCount,
        long participantCount,
        int maxEntriesPerUser,
        int myEntryCount,
        int remainingEntryCount,
        int voteLimit,
        long usedVoteCount,
        long remainingVoteCount
) {

    public static ContestResponse of(
            Contest contest,
            ContestPhase phase,
            int entryCount,
            long participantCount,
            int myEntryCount,
            long usedVoteCount
    ) {
        int remainingEntryCount = Math.max(0, contest.getMaxEntriesPerUser() - myEntryCount);
        long remainingVoteCount = Math.max(0, contest.getVoteLimit() - usedVoteCount);

        return new ContestResponse(
                contest.getId(),
                contest.getTitle(),
                contest.getDescription(),
                contest.getThemeImageUrl(),
                phase,
                contest.getSubmitStartAt(),
                contest.getSubmitEndAt(),
                contest.getVoteStartAt(),
                contest.getVoteEndAt(),
                contest.getResultOpenAt(),
                entryCount,
                participantCount,
                contest.getMaxEntriesPerUser(),
                myEntryCount,
                remainingEntryCount,
                contest.getVoteLimit(),
                usedVoteCount,
                remainingVoteCount
        );
    }
}
