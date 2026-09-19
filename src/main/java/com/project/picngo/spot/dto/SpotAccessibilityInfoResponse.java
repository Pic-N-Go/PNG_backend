package com.project.picngo.spot.dto;

import com.project.picngo.spot.domain.SpotAccessibilityInfo;

public record SpotAccessibilityInfoResponse(
        Long spotId,
        String parking,
        String publicTransport,
        String route,
        String ticketOffice,
        String promotion,
        String wheelchair,
        String entranceExit,
        String elevator,
        String restroom,
        String auditorium,
        String room,
        String physicalDisabilityEtc,
        String brailleBlock,
        String helpDog,
        String humanGuide,
        String audioGuide,
        String largePrint,
        String braillePromotion,
        String guideSystem,
        String visualDisabilityEtc,
        String signGuide,
        String videoGuide,
        String hearingRoom,
        String hearingDisabilityEtc,
        String stroller,
        String lactationRoom,
        String babyChair,
        String infantFamilyEtc
) {
    public static SpotAccessibilityInfoResponse from(SpotAccessibilityInfo info) {
        return new SpotAccessibilityInfoResponse(
                info.getSpot().getId(),
                info.getParking(),
                info.getPublicTransport(),
                info.getRoute(),
                info.getTicketOffice(),
                info.getPromotion(),
                info.getWheelchair(),
                info.getEntranceExit(),
                info.getElevator(),
                info.getRestroom(),
                info.getAuditorium(),
                info.getRoom(),
                info.getPhysicalDisabilityEtc(),
                info.getBrailleBlock(),
                info.getHelpDog(),
                info.getHumanGuide(),
                info.getAudioGuide(),
                info.getLargePrint(),
                info.getBraillePromotion(),
                info.getGuideSystem(),
                info.getVisualDisabilityEtc(),
                info.getSignGuide(),
                info.getVideoGuide(),
                info.getHearingRoom(),
                info.getHearingDisabilityEtc(),
                info.getStroller(),
                info.getLactationRoom(),
                info.getBabyChair(),
                info.getInfantFamilyEtc()
        );
    }
}
