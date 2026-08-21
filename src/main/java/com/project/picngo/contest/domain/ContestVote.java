package com.project.picngo.contest.domain;

import com.project.picngo.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_contest_vote_user_entry",
                        columnNames = {"user_id", "entry_id"}
                )
        }
)
public class ContestVote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contest_id", nullable = false)
    private Contest contest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entry_id", nullable = false)
    private ContestEntry entry;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public static ContestVote create(Contest contest, ContestEntry entry, User user) {
        ContestVote vote = new ContestVote();
        vote.contest = contest;
        vote.entry = entry;
        vote.user = user;
        vote.createdAt = LocalDateTime.now();
        return vote;
    }
}
