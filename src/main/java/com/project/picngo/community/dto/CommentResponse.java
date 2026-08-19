package com.project.picngo.community.dto;

import java.time.LocalDateTime;

public record CommentResponse(
        Long id,
        String content,
        PostAuthorResponse author,
        /** 답글이면 원 댓글 ID, 최상위 댓글이면 null */
        Long parentId,
        /** 최상위 댓글에 달린 답글 수. 답글 자신은 항상 0이다. */
        int replyCount,
        long likeCount,
        /** 요청자가 좋아요를 눌렀는지. 비로그인이면 항상 false다. */
        boolean liked,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
