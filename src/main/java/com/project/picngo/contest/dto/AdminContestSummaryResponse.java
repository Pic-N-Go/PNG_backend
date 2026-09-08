package com.project.picngo.contest.dto;

import com.project.picngo.contest.domain.Contest;
import com.project.picngo.contest.domain.ContestPhase;

import java.time.LocalDateTime;

public record AdminContestSummaryResponse(
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
        int maxEntriesPerUser,
        int voteLimit,
        boolean active,
        boolean startNotificationSent,
        boolean resultNotificationSent,
        long totalEntries,
        long totalVotes,
        LocalDateTime createdAt
) {
    public static AdminContestSummaryResponse of(
            Contest contest,
            String themeImageUrl,
            ContestPhase phase,
            long totalEntries,
            long totalVotes
    ) {
        return new AdminContestSummaryResponse(
                contest.getId(),
                contest.getTitle(),
                contest.getDescription(),
                themeImageUrl,
                phase,
                contest.getSubmitStartAt(),
                contest.getSubmitEndAt(),
                contest.getVoteStartAt(),
                contest.getVoteEndAt(),
                contest.getResultOpenAt(),
                contest.getMaxEntriesPerUser(),
                contest.getVoteLimit(),
                contest.isActive(),
                contest.isStartNotificationSent(),
                contest.isResultNotificationSent(),
                totalEntries,
                totalVotes,
                contest.getCreatedAt()
        );
    }

    public static AdminContestSummaryResponse of(
            Contest contest,
            ContestPhase phase,
            long totalEntries,
            long totalVotes
    ) {
        return of(contest, contest.getThemeImageUrl(), phase, totalEntries, totalVotes);
    }
}
