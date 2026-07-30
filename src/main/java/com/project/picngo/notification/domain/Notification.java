package com.project.picngo.notification.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Table(indexes = {
        @Index(name = "idx_notification_user_created_at", columnList = "userId, createdAt"),
        @Index(name = "idx_notification_created_at", columnList = "createdAt")
})
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 50)
    private String type; // e.g. GOLDEN_HOUR, WISHLIST

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private Boolean isRead = false;

    private String deepLink;

    private Long spotId;

    // 멱등키: 같은 논리적 알림(재전달·중복 발행)의 중복 저장/발송을 막기 위한 유니크 키.
    // 형식 예) WEATHER_MATCH:{userId}:{spotId}:{targetDate}:{timeCondition}. null이면 중복 검사 대상이 아님(TEST 등).
    @Column(unique = true)
    private String dedupeKey;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public Notification(Long userId, String type, String title, String content, String deepLink, Long spotId, String dedupeKey) {
        this.userId = userId;
        this.type = type;
        this.title = title;
        this.content = content;
        this.deepLink = deepLink;
        this.spotId = spotId;
        this.dedupeKey = dedupeKey;
        this.isRead = false;
    }

    public void markAsRead() {
        this.isRead = true;
    }
}



