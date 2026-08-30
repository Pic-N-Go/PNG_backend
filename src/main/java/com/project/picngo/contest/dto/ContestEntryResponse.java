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
        // 순위와 같은 규칙으로 가린다 — 투표 기간에는 개별 작품의 득표수를 공개하지 않는다
        Integer voteCount,
        Integer rank,
        boolean voted,
        boolean mine,
        // EXIF에서 뽑은 촬영 시각. EXIF가 없는 사진이면 null이다
        LocalDateTime shotAt,
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
                showRanking ? entry.getVoteCount() : null,
                showRanking ? rank : null,
                voted,
                mine,
                entry.getShotAt(),
                entry.getCreatedAt()
        );
    }
}
