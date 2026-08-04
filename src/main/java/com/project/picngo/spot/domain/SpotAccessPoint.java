package com.project.picngo.spot.domain;

import com.project.picngo.common.domain.BaseTimeEntity;
import com.project.picngo.spot.domain.enums.AccessPointSource;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
// 한 스팟에는 여러 진입점 후보(관리자 수동 지정, 유저 피드백 등)가 함께 쌓일 수 있다.
// "대표 진입점은 하나"라는 불변식은 이 테이블에 유니크 제약을 거는 대신
// Spot.primaryAccessPoint 단일 참조로 강제한다(참조 컬럼은 값을 하나만 가질 수 있으므로
// 구조적으로 둘 이상의 대표를 가리킬 수 없다). 그래야 non-primary 후보가 여러 개
// 남아 있어야 하는 상황에서도 이 테이블 자체엔 개수 제약이 걸리지 않는다.
@Table(name = "spot_access_point")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SpotAccessPoint extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spot_id", nullable = false)
    private Spot spot;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column(length = 100)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AccessPointSource source;

    private LocalDateTime verifiedAt;

    @Builder
    public SpotAccessPoint(Spot spot, Double latitude, Double longitude, String label, AccessPointSource source, LocalDateTime verifiedAt) {
        this.spot = spot;
        this.latitude = latitude;
        this.longitude = longitude;
        this.label = label;
        this.source = source != null ? source : AccessPointSource.KAKAO_LOCAL;
        this.verifiedAt = verifiedAt != null ? verifiedAt : LocalDateTime.now();
    }
}
