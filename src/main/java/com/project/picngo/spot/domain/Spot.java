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

    @Column(nullable = false, length = 255)
    private String address;

    @Column(length = 20)
    private String zipcode;

    @Column(columnDefinition = "TEXT")
    private String overview;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private SpotCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SpotSource source;

    @Column(nullable = false)
    private Boolean badge = false;

    @Column(name = "tour_content_id", length = 100)
    private String tourContentId;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SpotStatus status;

    @Column(length = 255)
    private String usetime;

    @Column(length = 255)
    private String restdate;

    @Column(length = 255)
    private String infocenter;

    @Column(length = 255)
    private String parking;

    @Column(name = "wheelchair_access", length = 255)
    private String wheelchairAccess;

    @Column(name = "stroller_access", length = 255)
    private String strollerAccess;

    @Column(name = "pet_friendly", length = 255)
    private String petFriendly;

    @Column(name = "subway_access", length = 255)
    private String subwayAccess;

    @Column(name = "bookmark_count", nullable = false)
    private Integer bookmarkCount = 0;

    @Column(name = "review_count", nullable = false)
    private Integer reviewCount = 0;

    @Column(name = "photogenic_score", nullable = false)
    private Integer photogenicScore = 0;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "review_average", nullable = false)
    private Double reviewAverage = 0.0;

    @Column(nullable = false)
    private Boolean toilet = false;

    @Builder
    public Spot(
            String name,
            String address,
            String zipcode,
            String overview,
            Double latitude,
            Double longitude,
            SpotCategory category,
            SpotSource source,
            Boolean badge,
            String tourContentId,
            String imageUrl,
            String thumbnailUrl,
            SpotStatus status,
            String usetime,
            String restdate,
            String infocenter,
            String parking,
            String wheelchairAccess,
            String strollerAccess,
            String petFriendly,
            String subwayAccess,
            Integer bookmarkCount,
            Integer reviewCount,
            Integer photogenicScore,
            Boolean isActive,
            Double reviewAverage,
            Boolean toilet
    ) {
        this.name = name;
        this.address = address;
        this.zipcode = zipcode;
        this.overview = overview;
        this.latitude = latitude;
        this.longitude = longitude;
        this.category = category;
        this.source = source;
        this.badge = badge == null ? false : badge;
        this.tourContentId = tourContentId;
        this.imageUrl = imageUrl;
        this.thumbnailUrl = thumbnailUrl;
        this.status = status == null ? SpotStatus.PENDING : status;
        this.usetime = usetime;
        this.restdate = restdate;
        this.infocenter = infocenter;
        this.parking = parking;
        this.wheelchairAccess = wheelchairAccess;
        this.strollerAccess = strollerAccess;
        this.petFriendly = petFriendly;
        this.subwayAccess = subwayAccess;
        this.bookmarkCount = bookmarkCount == null ? 0 : bookmarkCount;
        this.reviewCount = reviewCount == null ? 0 : reviewCount;
        this.photogenicScore = photogenicScore == null ? 0 : photogenicScore;
        this.isActive = isActive == null ? true : isActive;
        this.reviewAverage = reviewAverage == null ? 0.0 : reviewAverage;
        this.toilet = toilet == null ? false : toilet;
    }
}

