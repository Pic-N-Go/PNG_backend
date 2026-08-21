package com.project.picngo.contest.dto;

import java.util.List;

public record ContestPastPageResponse(
        List<ContestPastResponse> contests,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last
) {
}
