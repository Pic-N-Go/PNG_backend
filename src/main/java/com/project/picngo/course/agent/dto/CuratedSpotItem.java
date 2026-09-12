package com.project.picngo.course.agent.dto;

public record CuratedSpotItem(
        Long spotId,
        String spotName,
        int dayNumber,
        int sequenceOrder,
        String photographyTip,
        int estimatedTravelMinutes
) {
    public CuratedSpotItem(Long spotId, String spotName, int sequenceOrder, String photographyTip, int estimatedTravelMinutes) {
        this(spotId, spotName, 1, sequenceOrder, photographyTip, estimatedTravelMinutes);
    }
}
