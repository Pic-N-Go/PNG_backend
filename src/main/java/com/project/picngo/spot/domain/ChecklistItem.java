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

    @Comment("항목 내용. 예: 삼각대 필요")
    @Column(nullable = false, length = 100)
    private String content;

    @Comment("표시 순서")
    @Column(nullable = false)
    private Integer orderIndex;

    @Builder
    public ChecklistItem(Spot spot, String content, Integer orderIndex) {
        this.spot = spot;
        this.content = content;
        this.orderIndex = orderIndex;
    }
}
