package com.project.picngo.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CommentCreateRequest(
        @NotBlank @Size(max = 500) String content,

        /**
         * 답글로 달 원 댓글 ID. 없으면 최상위 댓글이 된다.
         * 답글의 ID를 넣어도 서비스가 그 답글의 부모로 바꿔 붙인다(깊이 1단계 유지).
         */
        @Positive Long parentId
) {
}
