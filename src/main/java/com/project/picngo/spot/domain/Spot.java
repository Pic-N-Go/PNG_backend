package com.project.picngo.spot.domain;

import com.project.picngo.common.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Spot extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private SpotCategory category;

    @Column(nullable = false, length = 255)
    private String address;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column(length = 500)
    private String thumbnailUrl;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private Integer bookmarkCount = 0;

    @Column(nullable = false)
    private Integer reviewCount = 0;

    @Column(nullable = false)
    private Integer photogenicScore = 0;

    @Column(nullable = false)
    private Boolean isActive = true;

    @Builder
    public Spot(
            String name,
            SpotCategory category,
            String address,
            Double latitude,
            Double longitude,
            String thumbnailUrl,
            String description,
            Integer bookmarkCount,
            Integer reviewCount,
            Integer photogenicScore
    ) {
        this.name = name;
        this.category = category;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.thumbnailUrl = thumbnailUrl;
        this.description = description;
        this.bookmarkCount = bookmarkCount == null ? 0 : bookmarkCount;
        this.reviewCount = reviewCount == null ? 0 : reviewCount;
        this.photogenicScore = photogenicScore == null ? 0 : photogenicScore;
        this.isActive = true;
    }
}
