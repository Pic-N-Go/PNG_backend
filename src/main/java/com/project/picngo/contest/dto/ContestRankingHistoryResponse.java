package com.project.picngo.contest.dto;

import com.project.picngo.contest.domain.ContestPhase;

import java.time.LocalDate;
import java.util.List;

public record ContestRankingHistoryResponse(
        Long contestId,
        ContestPhase phase,
        List<Snapshot> snapshots
) {

    public record Snapshot(
            LocalDate snapshotDate,
            boolean completed,
            List<Ranking> rankings
    ) {
    }

    public record Ranking(
            int rank,
            Long entryId,
            String photoUrl,
            String authorNickname,
            /**
             * 출품자의 프로필 사진. 순위 그래프의 원형 썸네일이 쓴다 — 그 자리는 "누가"를 보여주는
             * 자리라 출품 사진(photoUrl)이 아니라 사람 사진이 들어간다. 아래 1~3위 목록의 사각
             * 썸네일은 반대로 photoUrl(출품 사진)을 쓴다.
             *
             * User.getDisplayProfileImage() 기준이라 올린 사진이 우선이고 없으면 소셜 사진으로
             * 떨어진다. 둘 다 없으면 null이고, 클라이언트는 그 경우 그라디언트 폴백을 그린다.
             */
            String authorProfileImageUrl,
            String spotName,
            int voteCount
    ) {
    }
}
