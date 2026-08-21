package com.project.picngo.contest.dto;

import com.project.picngo.contest.domain.ContestEntry;
import com.project.picngo.contest.domain.ContestPhase;

import java.time.LocalDateTime;

public record ContestEntryDetailResponse(
        Long entryId,
        String photoUrl,
        Long authorId,
        String authorNickname,
        String caption,
        Long spotId,
        String spotName,
        ContestPhase phase,
        int voteCount,
        Integer rank,
        boolean voted,
        boolean mine,
        boolean canVote,
        boolean canDelete,
        int voteLimit,
        long remainingVoteCount,
        LocalDateTime createdAt
) {

    public static ContestEntryDetailResponse from(
            ContestEntry entry,
            String photoUrl,
            ContestPhase phase,
            boolean showRanking,
            Integer rank,
            boolean voted,
            boolean mine,
            boolean canVote,
            boolean canDelete,
            int voteLimit,
            long remainingVoteCount
    ) {
        return new ContestEntryDetailResponse(
                entry.getId(),
                photoUrl,
                entry.getUser().getId(),
                entry.getUser().getNickname(),
                entry.getCaption(),
                entry.getSpot() != null ? entry.getSpot().getId() : null,
                entry.getSpotName(),
                phase,
                entry.getVoteCount(),
                showRanking ? rank : null,              // 공개 기간에만 순위 노출
                voted,
                mine,
                canVote,
                canDelete,
                voteLimit,
                remainingVoteCount,
                entry.getCreatedAt()
        );
    }
}
