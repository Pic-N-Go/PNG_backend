package com.project.picngo.spot.domain;

import com.project.picngo.common.domain.BaseTimeEntity;
import com.project.picngo.spot.domain.enums.SpotSource;
import com.project.picngo.spot.domain.enums.SpotStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

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
    @Column(length = 10)
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

    @Comment("카테고리. TourAPI: cat1/cat2/cat3")
    @Column(nullable = false, length = 50)
    private String category;

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
    @Column(length = 50)
    private String tourContentId;

    @Comment("대표 이미지 원본 URL. TourAPI: firstimage")
    @Column(length = 500)
    private String imageUrl;

    @Comment("대표 이미지 썸네일 URL. TourAPI: firstimage2")
    @Column(length = 500)
    private String thumbnailUrl;

    @Comment("스팟 승인 상태. PENDING | APPROVED | REJECTED")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SpotStatus status = SpotStatus.APPROVED;

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
    @Column(length = 100)
    private String wheelchairAccess;

    @Comment("유모차 대여 안내. TourAPI: chkbabycarriage")
    @Column(length = 100)
    private String strollerAccess;

    @Comment("반려동물 동반 안내. TourAPI: chkpet")
    @Column(length = 100)
    private String petFriendly;

    @Comment("대중교통 접근성. 예: 도보 10분")
    @Column(length = 50)
    private String subwayAccess;

    @Comment("북마크 수 (denormalized)")
    @Column(nullable = false)
    private Integer bookmarkCount = 0;

    @Comment("리뷰 수 (denormalized)")
    @Column(nullable = false)
    private Integer reviewCount = 0;

    @Comment("포토제닉 점수")
    @Column(nullable = false)
    private Integer photogenicScore = 0;

    @Comment("활성화 여부")
    @Column(nullable = false)
    private Boolean isActive = true;

    @Comment("리뷰 평균 별점 (denormalized)")
    @Column(nullable = false)
    private Double reviewAverage = 0.0;

    @Comment("화장실 여부")
    @Column(nullable = false)
    private Boolean toilet = false;

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

    @Builder
    public Spot(String name, String address, String zipcode, String overview,
                Double latitude, Double longitude,
                String category, String cat3, SpotSource source, Boolean badge, String tourContentId,
                String imageUrl, String thumbnailUrl, SpotStatus status, String usetime, String restdate,
                String infocenter, String parking, String wheelchairAccess,
                String strollerAccess, String petFriendly, String subwayAccess) {
        this.name = name;
        this.address = address;
        this.zipcode = zipcode;
        this.overview = overview;
        this.latitude = latitude;
        this.longitude = longitude;
        this.category = category;
        this.cat3 = cat3;
        this.source = source;
        this.badge = badge != null ? badge : false;
        this.tourContentId = tourContentId;
        this.imageUrl = imageUrl;
        this.thumbnailUrl = thumbnailUrl;
        this.status = status != null ? status : SpotStatus.APPROVED;
        this.usetime = usetime;
        this.restdate = restdate;
        this.infocenter = infocenter;
        this.parking = parking;
        this.wheelchairAccess = wheelchairAccess;
        this.strollerAccess = strollerAccess;
        this.petFriendly = petFriendly;
        this.subwayAccess = subwayAccess;
    }
}
