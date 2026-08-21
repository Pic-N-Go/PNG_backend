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
                        name = "uk_contest_subscription_user_contest",
                        columnNames = {"user_id", "contest_id"}
                )
        }
)
// 다음 콘테스트 알림 받기
public class ContestSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contest_id")
    private Contest contest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public static ContestSubscription create(Contest contest, User user) {
        ContestSubscription subscription = new ContestSubscription();
        subscription.contest = contest;
        subscription.user = user;
        subscription.createdAt = LocalDateTime.now();
        return subscription;
    }
}
