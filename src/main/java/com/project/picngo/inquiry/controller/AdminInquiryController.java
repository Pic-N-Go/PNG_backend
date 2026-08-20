package com.project.picngo.inquiry.controller;

import com.project.picngo.auth.service.CustomUserDetails;
import com.project.picngo.inquiry.domain.InquiryStatus;
import com.project.picngo.inquiry.dto.AdminInquiryAnswerRequest;
import com.project.picngo.inquiry.dto.InquiryResponse;
import com.project.picngo.inquiry.service.AdminInquiryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.project.picngo.inquiry.domain.InquiryType;

@RestController
@RequestMapping("/admin/inquiries")
@RequiredArgsConstructor
public class AdminInquiryController implements AdminInquiryControllerApiSpec {

    private final AdminInquiryService adminInquiryService;

    @Override
    @GetMapping
    public ResponseEntity<Page<InquiryResponse>> getInquiriesForAdmin(
            @RequestParam(required = false) InquiryType type,
            @RequestParam(required = false) InquiryStatus status,
            @RequestParam(required = false) Boolean isResolved,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(adminInquiryService.getInquiriesForAdmin(type, status, isResolved, keyword, page, size));
    }

    @Override
    @PostMapping("/{id}/answer")
    public ResponseEntity<InquiryResponse> answerInquiry(
            @AuthenticationPrincipal CustomUserDetails adminUserDetails,
            @PathVariable Long id,
            @Valid @RequestBody AdminInquiryAnswerRequest request
    ) {
        return ResponseEntity.ok(adminInquiryService.answerInquiry(adminUserDetails.getId(), id, request.answer()));
    }
}
