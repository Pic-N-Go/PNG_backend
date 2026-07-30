package com.project.picngo.community.dto;

import java.util.List;

public record PostPageResponse(
        List<PostResponse> posts,
        long totalElements,
        int totalPages,
        int page,
        int size,
        boolean hasNext
) {
}
