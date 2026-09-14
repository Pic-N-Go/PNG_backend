package com.project.picngo.report.controller;

import com.project.picngo.auth.service.CustomUserDetails;
import com.project.picngo.report.dto.ReportCreateRequest;
import com.project.picngo.report.dto.ReportCreateResponse;
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

@Tag(name = "신고 (Report)", description = "콘텐츠 신고 API")
public interface ReportControllerApiSpec {

    @Operation(
            summary = "게시글 신고",
            description = "게시글을 신고합니다. 본인의 게시글이나 이미 신고한 게시글은 신고할 수 없습니다.",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = @ApiResponse(responseCode = "201", description = "게시글 신고 접수 성공")
    )
    ResponseEntity<ReportCreateResponse> reportPost(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "신고할 게시글 ID", example = "1") @PathVariable Long postId,
            @Valid @RequestBody ReportCreateRequest request
    );

    @Operation(
            summary = "리뷰 신고",
            description = "스팟에 작성된 리뷰를 신고합니다. 본인의 리뷰나 이미 신고한 리뷰는 신고할 수 없습니다.",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = @ApiResponse(responseCode = "201", description = "리뷰 신고 접수 성공")
    )
    ResponseEntity<ReportCreateResponse> reportReview(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "신고할 리뷰 ID", example = "1") @PathVariable Long reviewId,
            @Valid @RequestBody ReportCreateRequest request
    );
}
