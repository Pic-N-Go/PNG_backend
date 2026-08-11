package com.project.picngo.contest.dto;

import java.util.List;

public record ContestEntryPageResponse(
        List<ContestEntryResponse> entries,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last
) {
}
