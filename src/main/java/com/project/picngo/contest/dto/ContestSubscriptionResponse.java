package com.project.picngo.contest.dto;

public record ContestSubscriptionResponse(
        Long contestId,
        boolean subscribed
) {
}
