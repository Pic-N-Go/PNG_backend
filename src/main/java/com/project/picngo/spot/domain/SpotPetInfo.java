package com.project.picngo.spot.domain;

import com.project.picngo.common.domain.BaseTimeEntity;
import com.project.picngo.external.dto.PetTourDetailResponse;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "spot_pet_info")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SpotPetInfo extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "spot_id", nullable = false, unique = true)
    private Spot spot;

    @Column(name = "accompany_type", length = 100)
    private String accompanyType;

    @Column(name = "allowed_companion", length = 255)
    private String allowedCompanion;

    @Column(name = "required_items", columnDefinition = "TEXT")
    private String requiredItems;

    @Column(name = "additional_info", columnDefinition = "TEXT")
    private String additionalInfo;

    @Column(columnDefinition = "TEXT")
    private String facilities;

    @Column(name = "provided_items", columnDefinition = "TEXT")
    private String providedItems;

    @Column(name = "purchasable_items", columnDefinition = "TEXT")
    private String purchasableItems;

    @Column(name = "rental_items", columnDefinition = "TEXT")
    private String rentalItems;

    @Column(name = "accident_risk_info", columnDefinition = "TEXT")
    private String accidentRiskInfo;

    public SpotPetInfo(Spot spot) {
        this.spot = spot;
    }

    public void update(PetTourDetailResponse.Item item) {
        this.accompanyType = blankToNull(item.acmpyTypeCd());
        this.allowedCompanion = blankToNull(item.acmpyPsblCpam());
        this.requiredItems = blankToNull(item.acmpyNeedMtr());
        this.additionalInfo = blankToNull(item.etcAcmpyInfo());
        this.facilities = blankToNull(item.relaPosesFclty());
        this.providedItems = blankToNull(item.relaFrnshPrdlst());
        this.purchasableItems = blankToNull(item.relaPurcPrdlst());
        this.rentalItems = blankToNull(item.relaRntlPrdlst());
        this.accidentRiskInfo = blankToNull(item.relaAcdntRiskMtr());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
