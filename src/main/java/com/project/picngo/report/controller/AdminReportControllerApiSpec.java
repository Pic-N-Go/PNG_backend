package com.project.picngo.report.controller;

import com.project.picngo.auth.service.CustomUserDetails;
import com.project.picngo.report.dto.AdminReportDetailResponse;
import com.project.picngo.report.dto.AdminReportListResponse;
import com.project.picngo.report.dto.AdminReportProcessRequest;
import com.project.picngo.report.dto.AdminReportProcessResponse;
import com.project.picngo.report.dto.AdminReportTargetDeleteRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "관리자 신고 (Admin Report)", description = "관리자 신고 처리 API")
public interface AdminReportControllerApiSpec {

    @Operation(summary = "전체 신고 목록 조회", security = @SecurityRequirement(name = "bearerAuth"))
    ResponseEntity<Page<AdminReportListResponse>> getAllReports(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    );

    @Operation(summary = "처리 대기 신고 목록 조회", security = @SecurityRequirement(name = "bearerAuth"))
    ResponseEntity<Page<AdminReportListResponse>> getPendingReports(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    );

    @Operation(summary = "조치 완료 신고 목록 조회", security = @SecurityRequirement(name = "bearerAuth"))
    ResponseEntity<Page<AdminReportListResponse>> getResolvedReports(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    );

    @Operation(summary = "신고 기각 목록 조회", security = @SecurityRequirement(name = "bearerAuth"))
    ResponseEntity<Page<AdminReportListResponse>> getDismissedReports(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    );

    @Operation(
            summary = "신고 상세 조회",
            description = "신고 내용, 신고 당시 콘텐츠, 신고자와 피신고자 및 처리 정보를 조회합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    ResponseEntity<AdminReportDetailResponse> getReport(
            @Parameter(description = "신고 ID", example = "1") @PathVariable Long reportId
    );

    @Operation(
            summary = "신고 처리",
            description = "접수된 신고를 RESOLVED 또는 DISMISSED 상태로 처리합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    ResponseEntity<AdminReportProcessResponse> processReport(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails adminUserDetails,
            @Parameter(description = "신고 ID", example = "1") @PathVariable Long reportId,
            @Valid @RequestBody AdminReportProcessRequest request
    );

    @Operation(
            summary = "신고 대상 삭제",
            description = "신고된 게시글 또는 리뷰를 삭제하고 같은 대상의 대기 중 신고를 모두 조치 완료 처리합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    ResponseEntity<AdminReportProcessResponse> deleteReportedTarget(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails adminUserDetails,
            @Parameter(description = "기준 신고 ID", example = "1") @PathVariable Long reportId,
            @Valid @RequestBody AdminReportTargetDeleteRequest request
    );
}
