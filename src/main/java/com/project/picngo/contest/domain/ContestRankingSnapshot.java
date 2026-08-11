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
                        name = "uk_contest_snapshot_rank",
                        columnNames = {"contest_id", "snapshot_date", "rank"}
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

    @Column(nullable = false)
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
