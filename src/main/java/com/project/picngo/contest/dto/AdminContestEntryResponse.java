package com.project.picngo.contest.dto;

import com.project.picngo.contest.domain.ContestEntry;

import java.time.LocalDateTime;

public record AdminContestEntryResponse(
        Long entryId,
        Long contestId,
        Long userId,
        String userNickname,
        String userProfileImageUrl,
        Long spotId,
        String spotName,
        String photoUrl,
        String caption,
        int voteCount,
        long reportCount,
        LocalDateTime createdAt
) {
    public static AdminContestEntryResponse of(ContestEntry entry, long reportCount) {
        return new AdminContestEntryResponse(
                entry.getId(),
                entry.getContest().getId(),
                entry.getUser().getId(),
                entry.getUser().getNickname(),
                entry.getUser().getProfileImageUrl(),
                entry.getSpot() != null ? entry.getSpot().getId() : null,
                entry.getSpot() != null ? entry.getSpot().getName() : null,
                entry.getPhotoUrl(),
                entry.getCaption(),
                entry.getVoteCount(),
                reportCount,
                entry.getCreatedAt()
        );
    }
}
