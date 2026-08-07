package com.project.picngo.spot.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChecklistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("체크리스트 항목 고유 ID")
    private Long id;

    @Comment("스팟 FK")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spot_id", nullable = false)
    private Spot spot;

    @Comment("유저 ID. null이면 시스템 기본 항목")
    @Column
    private Long userId;

    @Comment("항목 내용 (최대 20자)")
    @Column(nullable = false, length = 20)
    private String content;

    @Comment("표시 순서")
    @Column(nullable = false)
    private Integer orderIndex;

    @Builder
    public ChecklistItem(Spot spot, Long userId, String content, Integer orderIndex) {
        this.spot = spot;
        this.userId = userId;
        this.content = content;
        this.orderIndex = orderIndex;
    }
}
