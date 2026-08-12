package com.project.picngo.spot.domain;

import com.project.picngo.common.domain.BaseTimeEntity;
import com.project.picngo.spot.domain.enums.AccessType;
import com.project.picngo.spot.domain.enums.SpotSource;
import com.project.picngo.spot.domain.enums.SpotStatus;
import com.project.picngo.common.domain.SpotCategory;
import com.project.picngo.spot.dto.Coordinate;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
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
    @CollectionTable(
            name = "spot_categories",
            joinColumns = @JoinColumn(name = "spot_id"),
            // 없으면 data.sql의 ON DUPLICATE KEY가 안 먹어서 재기동마다 행이 쌓인다
            uniqueConstraints = @UniqueConstraint(
                    name = "uk_spot_categories", columnNames = {"spot_id", "category"}))
    @Enumerated(EnumType.STRING)
    // Hibernate 6.2+는 @Enumerated(STRING)을 MySQL 네이티브 ENUM 컬럼으로 만들고,
    // enum 값이 바뀔 때마다 기동 시 `modify column ... enum(...)` DDL을 날린다.
    // VARCHAR로 고정하면 그 DDL도, 스키마와 enum 목록의 결합도 사라진다.
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "category", length = 50)
    private Set<SpotCategory> categories = new HashSet<>();

    @Comment("TourAPI cat3 소분류 코드. 카테고리 태깅·시즌 보너스 매핑에 사용 (예: A01011200=해변)")
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

    @Comment("진입 도로 속성. ROAD_ACCESSIBLE | NEEDS_ENTRANCE | UNKNOWN")
    @Enumerated(EnumType.STRING)
    @Column(name = "access_type", nullable = false, length = 30)
    private AccessType accessType = AccessType.UNKNOWN;

    @OneToMany(mappedBy = "spot", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<SpotAccessPoint> accessPoints = new ArrayList<>();

    // 대표 진입점 참조 컬럼. 컬럼은 값을 하나만 가질 수 있으므로
    // "대표는 하나"라는 불변식이 구조적으로 성립한다(자식 테이블에 유니크 제약을 걸지 않아도 됨).
    // accessPoints EAGER 로딩과 같은 이유로 EAGER: 이 엔티티가 트랜잭션 경계를 넘나들며
    // 전달되는 곳(CourseFacade)에서 지연 로딩 예외가 나지 않아야 한다.
    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "primary_access_point_id")
    private SpotAccessPoint primaryAccessPoint;

    public void updateAccessType(AccessType accessType) {
        if (accessType != null) {
            this.accessType = accessType;
        }
    }

    public void addAccessPoint(SpotAccessPoint accessPoint) {
        if (this.accessPoints == null) {
            this.accessPoints = new ArrayList<>();
        }
        this.accessPoints.add(accessPoint);
    }

    public void assignPrimaryAccessPoint(SpotAccessPoint accessPoint) {
        this.primaryAccessPoint = accessPoint;
    }

    public Coordinate getNavigationTarget() {
        if (this.accessType == AccessType.NEEDS_ENTRANCE && this.primaryAccessPoint != null) {
            SpotAccessPoint ap = this.primaryAccessPoint;
            return new Coordinate(ap.getLatitude(), ap.getLongitude(), ap.getLabel());
        }
        return new Coordinate(this.latitude, this.longitude, this.name);
    }

    public void updateCategories(Set<SpotCategory> categories) {
        Set<SpotCategory> target = (categories != null) ? categories : Set.of();
        // clear()만 해도 Hibernate가 컬렉션 전체를 DELETE 후 재INSERT 한다.
        // 동기화 때 내용이 그대로인 스팟이 대부분이라 같으면 건너뛴다.
        if (this.categories.equals(target)) {
            return;
        }
        this.categories.clear();
        this.categories.addAll(target);
    }

    // 응답용 카테고리 이름. Set 순서가 비결정적이라 정렬된 List로 고정 (응답 안정성)
    public List<String> getCategoryNames() {
        if (categories == null || categories.isEmpty()) {
            log.debug("스팟 카테고리가 비어 있어 ETC 기본값으로 처리합니다. (spotId: {})", id);
            return List.of("ETC");
        }
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
