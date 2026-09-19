package com.project.picngo.spot.dto;

import com.project.picngo.spot.domain.SpotPetInfo;

public record SpotPetInfoResponse(
        Long spotId,
        String accompanyType,
        String allowedCompanion,
        String requiredItems,
        String additionalInfo,
        String facilities,
        String providedItems,
        String purchasableItems,
        String rentalItems,
        String accidentRiskInfo
) {
    public static SpotPetInfoResponse from(SpotPetInfo info) {
        return new SpotPetInfoResponse(
                info.getSpot().getId(),
                info.getAccompanyType(),
                info.getAllowedCompanion(),
                info.getRequiredItems(),
                info.getAdditionalInfo(),
                info.getFacilities(),
                info.getProvidedItems(),
                info.getPurchasableItems(),
                info.getRentalItems(),
                info.getAccidentRiskInfo()
        );
    }
}
