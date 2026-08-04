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
// 한 스팟의 대표 진입점은 하나여야 한다.
// 제약이 없으면 동시 요청이 각자 조회 후 저장해 primary 행이 둘 생기고,
// 이후 findBySpotIdAndIsPrimaryTrue가 Optional에 담지 못해 조회할 때마다 실패한다.
@Table(name = "spot_access_point",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_spot_access_point_primary",
                columnNames = {"spot_id", "is_primary"}))
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

    @Column(name = "is_primary", nullable = false)
    private Boolean isPrimary = true;

    private LocalDateTime verifiedAt;

    @Builder
    public SpotAccessPoint(Spot spot, Double latitude, Double longitude, String label, AccessPointSource source, Boolean isPrimary, LocalDateTime verifiedAt) {
        this.spot = spot;
        this.latitude = latitude;
        this.longitude = longitude;
        this.label = label;
        this.source = source != null ? source : AccessPointSource.KAKAO_LOCAL;
        this.isPrimary = isPrimary != null ? isPrimary : true;
        this.verifiedAt = verifiedAt != null ? verifiedAt : LocalDateTime.now();
    }

    public void updateAsPrimary(boolean isPrimary) {
        this.isPrimary = isPrimary;
    }
}
