package com.project.picngo.contest.dto;

import com.project.picngo.contest.domain.Contest;
import com.project.picngo.contest.domain.ContestPhase;

import java.time.LocalDateTime;

public record AdminContestDetailResponse(
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
        long subscriberCount,
        long totalEntries,
        long participantCount,
        long totalVotes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static AdminContestDetailResponse of(
            Contest contest,
            String themeImageUrl,
            ContestPhase phase,
            long subscriberCount,
            long totalEntries,
            long participantCount,
            long totalVotes
    ) {
        return new AdminContestDetailResponse(
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
                subscriberCount,
                totalEntries,
                participantCount,
                totalVotes,
                contest.getCreatedAt(),
                contest.getUpdatedAt()
        );
    }

    public static AdminContestDetailResponse of(
            Contest contest,
            ContestPhase phase,
            long subscriberCount,
            long totalEntries,
            long participantCount,
            long totalVotes
    ) {
        return of(contest, contest.getThemeImageUrl(), phase, subscriberCount, totalEntries, participantCount, totalVotes);
    }
}
