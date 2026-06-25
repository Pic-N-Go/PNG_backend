package com.project.picngo.spot.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"spot_id", "user_id"}))
public class Bookmark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("북마크 고유 ID")
    private Long id;

    @Comment("스팟 FK")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spot_id", nullable = false)
    private Spot spot;

    @Comment("유저 ID")
    @Column(nullable = false)
    private Long userId;

    @Comment("북마크 등록일시")
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public Bookmark(Spot spot, Long userId) {
        this.spot = spot;
        this.userId = userId;
        this.createdAt = LocalDateTime.now();
    }
}
