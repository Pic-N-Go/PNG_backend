package com.project.picngo.community.controller;

import com.project.picngo.auth.service.CustomUserDetails;
import com.project.picngo.community.dto.CommentCreateRequest;
import com.project.picngo.community.dto.CommentPageResponse;
import com.project.picngo.community.dto.CommentResponse;
import com.project.picngo.community.dto.CommentUpdateRequest;
import com.project.picngo.community.dto.ReactionResponse;
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

    @Operation(
            summary = "댓글 목록 조회",
            description = """
                    게시글의 최상위 댓글을 작성 시각 오름차순으로 조회합니다. 답글은 포함되지 않으며
                    각 댓글의 replyCount로 개수만 알려줍니다(답글은 별도 API로 조회).
                    토큰을 보내면 liked가 요청자 기준으로 채워집니다.
                    """
    )
    ResponseEntity<CommentPageResponse> getComments(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "게시글 ID", example = "4") @PathVariable Long postId,
            @Parameter(description = "페이지 번호(0부터 시작)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기(1~100)", example = "20")
            @RequestParam(defaultValue = "20") int size
    );

    @Operation(
            summary = "답글 목록 조회",
            description = "특정 댓글에 달린 답글을 작성 시각 오름차순으로 조회합니다."
    )
    ResponseEntity<CommentPageResponse> getReplies(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "게시글 ID", example = "4") @PathVariable Long postId,
            @Parameter(description = "원 댓글 ID", example = "10") @PathVariable Long commentId,
            @Parameter(description = "페이지 번호(0부터 시작)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기(1~100)", example = "20")
            @RequestParam(defaultValue = "20") int size
    );

    @Operation(
            summary = "댓글 작성",
            description = """
                    게시글에 댓글을 작성하고 게시글 댓글 수를 증가시킵니다.
                    parentId를 함께 보내면 그 댓글의 답글이 됩니다. 답글의 ID를 보내면 해당 답글의
                    원 댓글에 붙습니다(깊이는 1단계로 고정).
                    """,
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
            description = """
                    댓글 작성자만 삭제할 수 있습니다. 답글이 달린 댓글을 지우면 답글도 함께 삭제되며,
                    게시글 댓글 수는 지워진 개수만큼 줄어듭니다.
                    """,
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = @ApiResponse(responseCode = "204", description = "댓글 삭제 성공")
    )
    ResponseEntity<Void> deleteComment(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "게시글 ID", example = "4") @PathVariable Long postId,
            @Parameter(description = "댓글 ID", example = "10") @PathVariable Long commentId
    );

    @Operation(
            summary = "댓글 좋아요",
            description = "이미 눌렀다면 아무 일도 일어나지 않고 현재 상태를 그대로 돌려줍니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    ResponseEntity<ReactionResponse> like(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "게시글 ID", example = "4") @PathVariable Long postId,
            @Parameter(description = "댓글 ID", example = "10") @PathVariable Long commentId
    );

    @Operation(
            summary = "댓글 좋아요 취소",
            description = "누르지 않은 상태면 아무 일도 일어나지 않습니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    ResponseEntity<ReactionResponse> unlike(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "게시글 ID", example = "4") @PathVariable Long postId,
            @Parameter(description = "댓글 ID", example = "10") @PathVariable Long commentId
    );
}
