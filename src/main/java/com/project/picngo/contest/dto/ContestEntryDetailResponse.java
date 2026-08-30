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
        // 순위와 같은 규칙으로 가린다 — 투표 기간에는 개별 작품의 득표수를 공개하지 않는다
        Integer voteCount,
        Integer rank,
        boolean voted,
        boolean mine,
        boolean canVote,
        boolean canDelete,
        int voteLimit,
        long remainingVoteCount,
        // EXIF에서 뽑은 촬영 시각. EXIF가 없는 사진이면 null이다
        LocalDateTime shotAt,
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
                showRanking ? entry.getVoteCount() : null,
                showRanking ? rank : null,
                voted,
                mine,
                canVote,
                canDelete,
                voteLimit,
                remainingVoteCount,
                entry.getShotAt(),
                entry.getCreatedAt()
        );
    }
}
