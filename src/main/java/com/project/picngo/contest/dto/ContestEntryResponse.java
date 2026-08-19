package com.project.picngo.contest.dto;

import com.project.picngo.contest.domain.ContestEntry;

import java.time.LocalDateTime;

public record ContestEntryResponse(
        Long entryId,
        String photoUrl,
        Long authorId,
        String authorNickname,
        String caption,
        Long spotId,
        String spotName,
        int voteCount,
        Integer rank,
        boolean voted,
        boolean mine,
        LocalDateTime createdAt
) {

    public static ContestEntryResponse from(
            ContestEntry entry,
            String photoUrl,
            boolean showRanking,
            Integer rank,
            boolean voted,
            boolean mine
    ) {
        return new ContestEntryResponse(
                entry.getId(),
                photoUrl,
                entry.getUser().getId(),
                entry.getUser().getNickname(),
                entry.getCaption(),
                entry.getSpot() != null ? entry.getSpot().getId() : null,
                entry.getSpotName(),
                entry.getVoteCount(),
                showRanking ? rank : null,              // 순위 공개 여부
                voted,
                mine,
                entry.getCreatedAt()
        );
    }
}
