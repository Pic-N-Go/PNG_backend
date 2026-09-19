package com.project.picngo.spot.dto;

public record PetTourMatchStatusResponse(
        int contentTypeId,
        int apiTotalCount,
        int receivedCount,
        int matchedSpotCount
) {}
