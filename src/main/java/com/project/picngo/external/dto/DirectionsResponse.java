package com.project.picngo.external.dto;

public record DirectionsResponse(
        Integer travelTimeMinutes,
        Integer travelDistanceMeters,
        Integer resultCode
) {
    public DirectionsResponse(Integer travelTimeMinutes, Integer travelDistanceMeters) {
        this(travelTimeMinutes, travelDistanceMeters, 0);
    }
}
