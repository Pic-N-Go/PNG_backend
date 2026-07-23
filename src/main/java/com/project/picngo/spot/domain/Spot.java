package com.project.picngo.spot.domain;

import com.project.picngo.common.domain.BaseTimeEntity;
import com.project.picngo.spot.domain.enums.SpotSource;
import com.project.picngo.spot.domain.enums.SpotStatus;
import com.project.picngo.common.domain.SpotCategory;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Spot extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("스팟 고유 ID")
    private Long id;

    @Comment("스팟명. TourAPI: title")
    @Column(nullable = false, length = 100)
    private String name;

    @Comment("주소. TourAPI: addr1 + addr2")
    @Column(nullable = false, length = 255)
    private String address;

    @Comment("우편번호. TourAPI: zipcode")
    @Column(length = 20)
    private String zipcode;

    @Comment("스팟 개요/설명. TourAPI: overview")
    @Column(columnDefinition = "TEXT")
    private String overview;

    @Comment("GPS 위도. TourAPI: mapy")
    @Column(nullable = false)
    private Double latitude;

    @Comment("GPS 경도. TourAPI: mapx")
    @Column(nullable = false)
    private Double longitude;

    @Comment("사진테마 카테고리(다중). cat3 + overview 키워드로 태깅. 태그 없으면 ETC")
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "spot_categories", joinColumns = @JoinColumn(name = "spot_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 50)
    private Set<SpotCategory> categories = new HashSet<>();

    @Comment("TourAPI cat3 소분류 코드. 체크리스트 매핑에 사용 (예: A0201=해수욕장)")
    @Column(length = 10)
    private String cat3;

    @Comment("데이터 출처. TOUR_API | USER")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SpotSource source;

    @Comment("관광공사 인증 여부. source=TOUR_API면 true")
    @Column(nullable = false)
    private Boolean badge = false;

    @Comment("TourAPI contentId. 사용자 등록 스팟은 null")
    @Column(name = "tour_content_id", length = 100)
    private String tourContentId;

    @Comment("대표 이미지 원본 URL. TourAPI: firstimage")
    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Comment("대표 이미지 썸네일 URL. TourAPI: firstimage2")
    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Comment("스팟 승인 상태. PENDING | APPROVED | REJECTED")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SpotStatus status;

    @Comment("이용시간. TourAPI: usetime")
    @Column(columnDefinition = "TEXT")
    private String usetime;

    @Comment("쉬는날/휴무일. TourAPI: restdate")
    @Column(columnDefinition = "TEXT")
    private String restdate;

    @Comment("문의 및 안내 전화. TourAPI: infocenter")
    @Column(columnDefinition = "TEXT")
    private String infocenter;

    @Comment("주차 안내. TourAPI: parking")
    @Column(columnDefinition = "TEXT")
    private String parking;

    @Comment("장애인 시설 안내. TourAPI: chkhandicap")
    @Column(name = "wheelchair_access", length = 255)
    private String wheelchairAccess;

    @Comment("유모차 대여 안내. TourAPI: chkbabycarriage")
    @Column(name = "stroller_access", length = 255)
    private String strollerAccess;

    @Comment("반려동물 동반 안내. TourAPI: chkpet")
    @Column(name = "pet_friendly", length = 255)
    private String petFriendly;

    @Comment("대중교통 접근성. 예: 도보 10분")
    @Column(name = "subway_access", length = 255)
    private String subwayAccess;

    @Comment("북마크 수 (denormalized)")
    @Column(name = "bookmark_count", nullable = false)
    private int bookmarkCount = 0;

    @Comment("리뷰 수 (denormalized)")
    @Column(name = "review_count", nullable = false)
    private int reviewCount = 0;

    @Comment("포토제닉 점수")
    @Column(name = "photogenic_score", nullable = false)
    private int photogenicScore = 0;

    @Comment("활성화 여부")
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Comment("리뷰 평균 별점 (denormalized)")
    @Column(name = "review_average", nullable = false)
    private double reviewAverage = 0.0;

    @Comment("화장실 여부")
    @Column(nullable = false)
    private boolean toilet = false;

    public void updateCategories(Set<SpotCategory> categories) {
        this.categories.clear();
        if (categories != null) {
            this.categories.addAll(categories);
        }
    }

    // 응답용 카테고리 이름. Set 순서가 비결정적이라 정렬된 List로 고정 (응답 안정성)
    public List<String> getCategoryNames() {
        return categories.stream().map(Enum::name).sorted().toList();
    }

    public void updateFromTourApi(String overview, String parking, String usetime,
                                   String restdate, String infocenter,
                                   String wheelchairAccess, String strollerAccess, String petFriendly) {
        this.overview = overview;
        this.parking = parking;
        this.usetime = usetime;
        this.restdate = restdate;
        this.infocenter = infocenter;
        this.wheelchairAccess = wheelchairAccess;
        this.strollerAccess = strollerAccess;
        this.petFriendly = petFriendly;
    }

    public void incrementBookmarkCount() {
        this.bookmarkCount += 1;
    }

    public void decrementBookmarkCount() {
        this.bookmarkCount = Math.max(0, this.bookmarkCount - 1);
    }

    public void updateReviewStats(Double avgRating, int count) {
        this.reviewAverage = avgRating != null ? avgRating : 0.0;
        this.reviewCount = count;
    }

    @Builder
    public Spot(
            String name,
            String address,
            String zipcode,
            String overview,
            Double latitude,
            Double longitude,
            Set<SpotCategory> categories,
            String cat3,
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
        this.categories = (categories != null) ? new HashSet<>(categories) : new HashSet<>();
        this.cat3 = cat3;
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
