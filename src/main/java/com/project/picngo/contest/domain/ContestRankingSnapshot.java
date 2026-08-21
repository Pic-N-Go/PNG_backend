package com.project.picngo.contest.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_contest_snapshot_entry",
                        columnNames = {"contest_id", "snapshot_date", "entry_id"}
                )
        }
)
public class ContestRankingSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contest_id", nullable = false)
    private Contest contest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entry_id", nullable = false)
    private ContestEntry entry;

    @Column(nullable = false)
    private LocalDate snapshotDate; // 집계 날짜

    // 컬럼명이 필드명과 다른 이유: rank는 MySQL 8의 예약어라 따옴표 없이 쓸 수 없다.
    // 이름을 그대로 두면 create table이 문법 오류로 실패한다(실제로 이 테이블만 생성되지
    // 않았고, ddl-auto: update가 실패를 경고로만 남겨 한동안 드러나지 않았다).
    // 역따옴표로 감싸는 방법도 있지만, 그러면 이 컬럼을 건드리는 모든 SQL이 따옴표를
    // 챙겨야 한다. 예약어를 스키마에서 아예 빼는 쪽을 골랐다.
    @Column(name = "ranking", nullable = false)
    private int rank; // 집계 순위

    @Column(nullable = false)
    private int voteCount; // 집계 시점 득표 수

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public static ContestRankingSnapshot create(
            Contest contest,
            ContestEntry entry,
            LocalDate snapshotDate,
            int rank,
            int voteCount
    ) {
        ContestRankingSnapshot snapshot = new ContestRankingSnapshot();
        snapshot.contest = contest;
        snapshot.entry = entry;
        snapshot.snapshotDate = snapshotDate;
        snapshot.rank = rank;
        snapshot.voteCount = voteCount;
        snapshot.createdAt = LocalDateTime.now();
        return snapshot;
    }
}
