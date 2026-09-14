package com.project.picngo.report.controller;

import com.project.picngo.auth.service.CustomUserDetails;
import com.project.picngo.report.dto.ReportCreateRequest;
import com.project.picngo.report.dto.ReportCreateResponse;
import com.project.picngo.report.service.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportController implements ReportControllerApiSpec {

    private final ReportService reportService;

    @PostMapping("/posts/{postId}")
    public ResponseEntity<ReportCreateResponse> reportPost(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long postId,
            @Valid @RequestBody ReportCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reportService.reportPost(userDetails.getId(), postId, request));
    }

    @PostMapping("/reviews/{reviewId}")
    public ResponseEntity<ReportCreateResponse> reportReview(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long reviewId,
            @Valid @RequestBody ReportCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reportService.reportReview(userDetails.getId(), reviewId, request));
    }
}
