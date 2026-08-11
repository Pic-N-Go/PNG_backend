package com.project.picngo.contest.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Contest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 500)
    private String description;

    @Column(length = 500)
    private String themeImageUrl;

    @Column(nullable = false)
    private LocalDateTime submitStartAt; // 출품 시작일

    @Column(nullable = false)
    private LocalDateTime submitEndAt; // 출품 종료일

    @Column(nullable = false)
    private LocalDateTime voteStartAt; // 투표 시작일

    @Column(nullable = false)
    private LocalDateTime voteEndAt; // 투표 종료일

    @Column(nullable = false)
    private LocalDateTime resultOpenAt; // 결과 발표일

    @Column(nullable = false)
    private int maxEntriesPerUser; // 1인 최대 출품 수

    @Column(nullable = false)
    private int voteLimit; // 콘테스트 기간 내 최대 투표 수

    @Column(nullable = false)
    private boolean active;

    public ContestPhase getPhase(LocalDateTime now) {
        if (now.isBefore(submitEndAt)) {
            return ContestPhase.SUBMITTING;
        }

        if (now.isBefore(voteEndAt)) {
            return ContestPhase.VOTING;
        }

        if (now.isBefore(resultOpenAt)) {
            return ContestPhase.RESULT;
        }

        return ContestPhase.ENDED;
    }
}
