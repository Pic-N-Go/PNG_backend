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
@Table(name = "photo_awards", indexes = {
        @Index(name = "idx_photo_award_content_id", columnList = "content_id", unique = true),
        @Index(name = "idx_photo_award_spot_id", columnList = "spot_id")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PhotoAward {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("사진공모전 수상작 고유 ID")
    private Long id;

    @Comment("한국관광공사 공모전 수상작 contentId")
    @Column(name = "content_id", nullable = false, length = 100, unique = true)
    private String contentId;

    @Comment("작품명")
    @Column(nullable = false, length = 200)
    private String title;

    @Comment("촬영자 / 작가명")
    @Column(length = 100)
    private String photographer;

    @Comment("촬영 연월 (YYYYMM)")
    @Column(name = "award_year_month", length = 10)
    private String awardYearMonth;

    @Comment("수상 내역 (예: 대상, 금상, 디지털카메라 부문 [은상])")
    @Column(name = "award_name", length = 100)
    private String awardName;

    @Comment("촬영 장소명 (koFilmst)")
    @Column(name = "location_name", length = 255)
    private String locationName;

    @Comment("원본 이미지 URL")
    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Comment("썸네일 이미지 URL")
    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Comment("저작권 유형 (예: Type1 - 공공누리 제1유형)")
    @Column(name = "copyright_type", length = 50)
    private String copyrightType;

    @Comment("법정동 시도 코드")
    @Column(name = "ldong_regn_cd")
    private Integer lDongRegnCd;

    @Comment("연계된 스팟 FK")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spot_id", nullable = false)
    private Spot spot;

    @Comment("등록 일시")
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public PhotoAward(String contentId, String title, String photographer,
                      String awardYearMonth, String awardName, String locationName,
                      String imageUrl, String thumbnailUrl, String copyrightType,
                      Integer lDongRegnCd, Spot spot) {
        this.contentId = contentId;
        this.title = title;
        this.photographer = photographer;
        this.awardYearMonth = awardYearMonth;
        this.awardName = awardName;
        this.locationName = locationName;
        this.imageUrl = imageUrl;
        this.thumbnailUrl = thumbnailUrl;
        this.copyrightType = copyrightType;
        this.lDongRegnCd = lDongRegnCd;
        this.spot = spot;
        this.createdAt = LocalDateTime.now();
    }
}
