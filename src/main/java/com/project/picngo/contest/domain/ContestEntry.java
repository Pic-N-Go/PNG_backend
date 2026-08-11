package com.project.picngo.contest.domain;

import com.project.picngo.spot.domain.Spot;
import com.project.picngo.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContestEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contest_id", nullable = false)
    private Contest contest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // S3 object key 저장
    @Column(nullable = false, length = 500)
    private String photoUrl;

    @Column(length = 80)
    private String caption;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spot_id")
    private Spot spot;

    @Column(length = 100)
    private String spotName;

    @Column(nullable = false)
    private int voteCount; // 받은 투표 수

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public static ContestEntry create(
            Contest contest,
            User user,
            String photoUrl,
            String caption,
            Spot spot,
            String spotName
    ) {
        ContestEntry entry = new ContestEntry();
        entry.contest = contest;
        entry.user = user;
        entry.photoUrl = photoUrl;
        entry.caption = caption;
        entry.spot = spot;
        entry.spotName = spotName;
        entry.voteCount = 0;
        entry.createdAt = LocalDateTime.now();
        return entry;
    }

    public void increaseVoteCount() {
        this.voteCount++;
    }

    public void decreaseVoteCount() {
        if (this.voteCount > 0) {
            this.voteCount--;
        }
    }
}
