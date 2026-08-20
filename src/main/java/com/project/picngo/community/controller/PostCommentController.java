package com.project.picngo.community.controller;

import com.project.picngo.auth.service.CustomUserDetails;
import com.project.picngo.community.dto.CommentCreateRequest;
import com.project.picngo.community.dto.CommentPageResponse;
import com.project.picngo.community.dto.CommentResponse;
import com.project.picngo.community.dto.CommentUpdateRequest;
import com.project.picngo.community.dto.ReactionResponse;
import com.project.picngo.community.service.PostCommentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/posts/{postId}/comments")
@RequiredArgsConstructor
public class PostCommentController implements PostCommentControllerApiSpec {

    private final PostCommentService commentService;

    @GetMapping
    public ResponseEntity<CommentPageResponse> getComments(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long postId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(commentService.getComments(postId, userId(userDetails), page, size));
    }

    @GetMapping("/{commentId}/replies")
    public ResponseEntity<CommentPageResponse> getReplies(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long postId,
            @PathVariable Long commentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(commentService.getReplies(postId, commentId, userId(userDetails), page, size));
    }

    @PostMapping
    public ResponseEntity<CommentResponse> createComment(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long postId,
            @Valid @RequestBody CommentCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(commentService.createComment(postId, userDetails.getId(), request));
    }

    @PatchMapping("/{commentId}")
    public ResponseEntity<CommentResponse> updateComment(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long postId,
            @PathVariable Long commentId,
            @Valid @RequestBody CommentUpdateRequest request
    ) {
        return ResponseEntity.ok(commentService.updateComment(postId, commentId, userDetails.getId(), request));
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long postId,
            @PathVariable Long commentId
    ) {
        commentService.deleteComment(postId, commentId, userDetails.getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{commentId}/like")
    public ResponseEntity<ReactionResponse> like(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long postId,
            @PathVariable Long commentId
    ) {
        return ResponseEntity.ok(commentService.like(postId, commentId, userDetails.getId()));
    }

    @DeleteMapping("/{commentId}/like")
    public ResponseEntity<ReactionResponse> unlike(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long postId,
            @PathVariable Long commentId
    ) {
        return ResponseEntity.ok(commentService.unlike(postId, commentId, userDetails.getId()));
    }

    /** 목록 조회는 비로그인도 허용된다(SecurityConfig의 GET /posts/**). 그때 userDetails가 null이다. */
    private Long userId(CustomUserDetails userDetails) {
        return userDetails == null ? null : userDetails.getId();
    }
}
