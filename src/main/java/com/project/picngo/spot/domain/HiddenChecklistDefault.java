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
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"spot_id", "user_id", "default_item_id"}))
public class HiddenChecklistDefault {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("숨김 처리 고유 ID")
    private Long id;

    @Comment("스팟 FK")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spot_id", nullable = false)
    private Spot spot;

    @Comment("유저 ID")
    @Column(nullable = false)
    private Long userId;

    // ponytail: ChecklistMapper 프리셋 리스트 내 1-based 순번. 별도 프리셋 테이블 없이 스팟(cat3) 범위 안에서만 안정적이면 충분
    @Comment("기본 체크리스트 프리셋 내 순번 (1-based)")
    @Column(nullable = false)
    private Integer defaultItemId;

    @Builder
    public HiddenChecklistDefault(Spot spot, Long userId, Integer defaultItemId) {
        this.spot = spot;
        this.userId = userId;
        this.defaultItemId = defaultItemId;
    }
}
