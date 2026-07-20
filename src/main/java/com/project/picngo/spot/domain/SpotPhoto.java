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
public class SpotPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("사진 고유 ID")
    private Long id;

    @Comment("스팟 FK")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spot_id", nullable = false)
    private Spot spot;

    @Comment("업로드한 유저 ID. TourAPI 사진은 null")
    @Column
    private Long userId;

    @Comment("사진 URL(원본)")
    @Column(nullable = false, length = 500)
    private String photoUrl;

    @Comment("썸네일 URL. TourAPI 사진만 존재, 유저 업로드는 null")
    @Column(length = 500)
    private String thumbnailUrl;

    @Comment("등록일시")
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public SpotPhoto(Spot spot, Long userId, String photoUrl, String thumbnailUrl) {
        this.spot = spot;
        this.userId = userId;
        this.photoUrl = photoUrl;
        this.thumbnailUrl = thumbnailUrl;
        this.createdAt = LocalDateTime.now();
    }
}
