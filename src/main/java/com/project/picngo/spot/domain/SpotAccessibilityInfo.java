package com.project.picngo.spot.domain;

import com.project.picngo.common.domain.BaseTimeEntity;
import com.project.picngo.external.dto.AccessibilityTourDetailResponse;
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
@Table(name = "spot_accessibility_info")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SpotAccessibilityInfo extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "spot_id", nullable = false, unique = true)
    private Spot spot;

    @Column(columnDefinition = "TEXT")
    private String parking;

    @Column(name = "public_transport", columnDefinition = "TEXT")
    private String publicTransport;

    @Column(columnDefinition = "TEXT")
    private String route;

    @Column(name = "ticket_office", columnDefinition = "TEXT")
    private String ticketOffice;

    @Column(columnDefinition = "TEXT")
    private String promotion;

    @Column(columnDefinition = "TEXT")
    private String wheelchair;

    @Column(name = "entrance_exit", columnDefinition = "TEXT")
    private String entranceExit;

    @Column(columnDefinition = "TEXT")
    private String elevator;

    @Column(columnDefinition = "TEXT")
    private String restroom;

    @Column(columnDefinition = "TEXT")
    private String auditorium;

    @Column(columnDefinition = "TEXT")
    private String room;

    @Column(name = "physical_disability_etc", columnDefinition = "TEXT")
    private String physicalDisabilityEtc;

    @Column(name = "braille_block", columnDefinition = "TEXT")
    private String brailleBlock;

    @Column(name = "help_dog", columnDefinition = "TEXT")
    private String helpDog;

    @Column(name = "human_guide", columnDefinition = "TEXT")
    private String humanGuide;

    @Column(name = "audio_guide", columnDefinition = "TEXT")
    private String audioGuide;

    @Column(name = "large_print", columnDefinition = "TEXT")
    private String largePrint;

    @Column(name = "braille_promotion", columnDefinition = "TEXT")
    private String braillePromotion;

    @Column(name = "guide_system", columnDefinition = "TEXT")
    private String guideSystem;

    @Column(name = "visual_disability_etc", columnDefinition = "TEXT")
    private String visualDisabilityEtc;

    @Column(name = "sign_guide", columnDefinition = "TEXT")
    private String signGuide;

    @Column(name = "video_guide", columnDefinition = "TEXT")
    private String videoGuide;

    @Column(name = "hearing_room", columnDefinition = "TEXT")
    private String hearingRoom;

    @Column(name = "hearing_disability_etc", columnDefinition = "TEXT")
    private String hearingDisabilityEtc;

    @Column(columnDefinition = "TEXT")
    private String stroller;

    @Column(name = "lactation_room", columnDefinition = "TEXT")
    private String lactationRoom;

    @Column(name = "baby_chair", columnDefinition = "TEXT")
    private String babyChair;

    @Column(name = "infant_family_etc", columnDefinition = "TEXT")
    private String infantFamilyEtc;

    public SpotAccessibilityInfo(Spot spot) {
        this.spot = spot;
    }

    public void update(AccessibilityTourDetailResponse.Item item) {
        this.parking = blankToNull(item.parking());
        this.publicTransport = blankToNull(item.publictransport());
        this.route = blankToNull(item.route());
        this.ticketOffice = blankToNull(item.ticketoffice());
        this.promotion = blankToNull(item.promotion());
        this.wheelchair = blankToNull(item.wheelchair());
        this.entranceExit = blankToNull(item.exit());
        this.elevator = blankToNull(item.elevator());
        this.restroom = blankToNull(item.restroom());
        this.auditorium = blankToNull(item.auditorium());
        this.room = blankToNull(item.room());
        this.physicalDisabilityEtc = blankToNull(item.handicapetc());
        this.brailleBlock = blankToNull(item.braileblock());
        this.helpDog = blankToNull(item.helpdog());
        this.humanGuide = blankToNull(item.guidehuman());
        this.audioGuide = blankToNull(item.audioguide());
        this.largePrint = blankToNull(item.bigprint());
        this.braillePromotion = blankToNull(item.brailepromotion());
        this.guideSystem = blankToNull(item.guidesystem());
        this.visualDisabilityEtc = blankToNull(item.blindhandicapetc());
        this.signGuide = blankToNull(item.signguide());
        this.videoGuide = blankToNull(item.videoguide());
        this.hearingRoom = blankToNull(item.hearingroom());
        this.hearingDisabilityEtc = blankToNull(item.hearinghandicapetc());
        this.stroller = blankToNull(item.stroller());
        this.lactationRoom = blankToNull(item.lactationroom());
        this.babyChair = blankToNull(item.babysparechair());
        this.infantFamilyEtc = blankToNull(item.infantsfamilyetc());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
