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
public class SpotTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("태그 고유 ID")
    private Long id;

    @Comment("스팟 FK")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spot_id", nullable = false)
    private Spot spot;

    @Comment("태그명. 예: #야경, #바다")
    @Column(nullable = false, length = 30)
    private String tag;

    @Builder
    public SpotTag(Spot spot, String tag) {
        this.spot = spot;
        this.tag = tag;
    }
}
