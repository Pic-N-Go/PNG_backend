package com.project.picngo.community.dto;

import java.time.LocalDateTime;

public record CommentResponse(
        Long id,
        String content,
        PostAuthorResponse author,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
