package com.project.picngo.community.controller;

import com.project.picngo.auth.service.CustomUserDetails;
import com.project.picngo.community.dto.CommentCreateRequest;
import com.project.picngo.community.dto.CommentPageResponse;
import com.project.picngo.community.dto.CommentResponse;
import com.project.picngo.community.dto.CommentUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "커뮤니티 댓글 (Post Comment)", description = "커뮤니티 게시글 댓글 조회 및 CRUD API")
public interface PostCommentControllerApiSpec {

    @Operation(summary = "댓글 목록 조회", description = "게시글의 댓글을 작성 시각 오름차순으로 조회합니다.")
    ResponseEntity<CommentPageResponse> getComments(
            @Parameter(description = "게시글 ID", example = "4") @PathVariable Long postId,
            @Parameter(description = "페이지 번호(0부터 시작)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기(1~100)", example = "20")
            @RequestParam(defaultValue = "20") int size
    );

    @Operation(
            summary = "댓글 작성",
            description = "게시글에 댓글을 작성하고 게시글 댓글 수를 증가시킵니다.",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = @ApiResponse(responseCode = "201", description = "댓글 작성 성공")
    )
    ResponseEntity<CommentResponse> createComment(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "게시글 ID", example = "4") @PathVariable Long postId,
            @Valid @RequestBody CommentCreateRequest request
    );

    @Operation(
            summary = "댓글 수정",
            description = "댓글 작성자만 수정할 수 있습니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    ResponseEntity<CommentResponse> updateComment(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "게시글 ID", example = "4") @PathVariable Long postId,
            @Parameter(description = "댓글 ID", example = "10") @PathVariable Long commentId,
            @Valid @RequestBody CommentUpdateRequest request
    );

    @Operation(
            summary = "댓글 삭제",
            description = "댓글 작성자만 삭제할 수 있으며 게시글 댓글 수를 감소시킵니다.",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = @ApiResponse(responseCode = "204", description = "댓글 삭제 성공")
    )
    ResponseEntity<Void> deleteComment(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "게시글 ID", example = "4") @PathVariable Long postId,
            @Parameter(description = "댓글 ID", example = "10") @PathVariable Long commentId
    );
}
