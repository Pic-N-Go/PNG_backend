package com.project.picngo.course.agent.dto;

public record CuratedSpotItem(
        Long spotId,
        String spotName,
        int sequenceOrder,
        String photographyTip,
        int estimatedTravelMinutes
) {}
