package com.project.picngo.contest.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ContestResultResponse(
        Long contestId,
        String title,
        // 결과 화면 상단의 기간 표기용 — 이것 없이는 /contests/{id}를 한 번 더 불러야 한다
        LocalDateTime submitStartAt,
        LocalDateTime voteEndAt,
        int entryCount,
        long participantCount,
        int totalVoteCount,
        ResultEntry winner,
        ResultEntry myResult,
        List<ResultEntry> rankings
) {

    public record ResultEntry(
            int rank,
            Long entryId,
            String photoUrl,
            /**
             * 출품자의 사용자 id. 클라이언트가 프로필 사진이 없을 때 그리는 아바타 폴백 색을
             * 이 값으로 고른다 — 같은 사람이 어느 화면에서든 같은 색이어야 해서, 닉네임이나
             * entryId로 대신하면 회차마다 색이 달라진다.
             */
            Long authorId,
            String authorNickname,
            /**
             * 출품자의 프로필 사진. 최종 순위 목록의 원형 아바타가 쓴다 — 그 자리는 "누가"를
             * 보여주는 자리라 출품 사진(photoUrl)이 아니라 사람 사진이 들어간다.
             *
             * User.getDisplayProfileImage() 기준이라 올린 사진이 우선이고 없으면 소셜 사진으로
             * 떨어진다. 둘 다 없으면 null이고, 클라이언트는 그 경우 그라디언트 폴백을 그린다.
             * ContestRankingHistoryResponse.Ranking.authorProfileImageUrl과 같은 규칙이다.
             */
            String authorProfileImageUrl,
            String caption,
            Long spotId,
            String spotName,
            int voteCount
    ) {
    }
}
