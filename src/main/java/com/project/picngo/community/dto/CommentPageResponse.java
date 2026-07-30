package com.project.picngo.community.dto;

import java.util.List;

public record CommentPageResponse(
        List<CommentResponse> comments,
        long totalElements,
        int totalPages,
        int page,
        int size,
        boolean hasNext
) {
}
