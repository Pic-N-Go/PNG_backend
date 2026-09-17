package com.project.picngo.report.controller;

import com.project.picngo.auth.service.CustomUserDetails;
import com.project.picngo.report.dto.AdminReportDetailResponse;
import com.project.picngo.report.dto.AdminReportListResponse;
import com.project.picngo.report.dto.AdminReportProcessRequest;
import com.project.picngo.report.dto.AdminReportProcessResponse;
import com.project.picngo.report.dto.AdminReportTargetDeleteRequest;
import com.project.picngo.report.service.AdminReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/reports")
@RequiredArgsConstructor
public class AdminReportController implements AdminReportControllerApiSpec {

    private final AdminReportService adminReportService;

    @Override
    @GetMapping
    public ResponseEntity<Page<AdminReportListResponse>> getAllReports(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(adminReportService.getAllReports(page, size));
    }

    @Override
    @GetMapping("/pending")
    public ResponseEntity<Page<AdminReportListResponse>> getPendingReports(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(adminReportService.getPendingReports(page, size));
    }

    @Override
    @GetMapping("/resolved")
    public ResponseEntity<Page<AdminReportListResponse>> getResolvedReports(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(adminReportService.getResolvedReports(page, size));
    }

    @Override
    @GetMapping("/dismissed")
    public ResponseEntity<Page<AdminReportListResponse>> getDismissedReports(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(adminReportService.getDismissedReports(page, size));
    }

    @Override
    @GetMapping("/{reportId}")
    public ResponseEntity<AdminReportDetailResponse> getReport(@PathVariable Long reportId) {
        return ResponseEntity.ok(adminReportService.getReport(reportId));
    }

    @Override
    @PatchMapping("/{reportId}")
    public ResponseEntity<AdminReportProcessResponse> processReport(
            @AuthenticationPrincipal CustomUserDetails adminUserDetails,
            @PathVariable Long reportId,
            @Valid @RequestBody AdminReportProcessRequest request
    ) {
        return ResponseEntity.ok(
                adminReportService.processReport(adminUserDetails.getId(), reportId, request)
        );
    }

    @Override
    @DeleteMapping("/{reportId}/target")
    public ResponseEntity<AdminReportProcessResponse> deleteReportedTarget(
            @AuthenticationPrincipal CustomUserDetails adminUserDetails,
            @PathVariable Long reportId,
            @Valid @RequestBody AdminReportTargetDeleteRequest request
    ) {
        return ResponseEntity.ok(
            adminReportService.deleteReportedTarget(adminUserDetails.getId(), reportId, request)
        );
    }
}
