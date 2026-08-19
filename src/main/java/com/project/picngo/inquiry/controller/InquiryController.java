package com.project.picngo.inquiry.controller;

import com.project.picngo.auth.service.CustomUserDetails;
import com.project.picngo.inquiry.dto.InquiryCreateRequest;
import com.project.picngo.inquiry.dto.InquiryResolveUpdateRequest;
import com.project.picngo.inquiry.dto.InquiryResponse;
import com.project.picngo.inquiry.service.InquiryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/inquiries")
@RequiredArgsConstructor
public class InquiryController implements InquiryControllerApiSpec {

    private final InquiryService inquiryService;

    @Override
    @PostMapping
    public ResponseEntity<InquiryResponse> createInquiry(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody InquiryCreateRequest request
    ) {
        InquiryResponse response = inquiryService.createInquiry(userDetails.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    @GetMapping("/me")
    public ResponseEntity<Page<InquiryResponse>> getMyInquiries(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(inquiryService.getMyInquiries(userDetails.getId(), page, size));
    }

    @Override
    @GetMapping("/{id}")
    public ResponseEntity<InquiryResponse> getInquiryDetail(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(inquiryService.getInquiryDetail(userDetails.getId(), id));
    }

    @Override
    @PatchMapping("/{id}/resolve")
    public ResponseEntity<InquiryResponse> updateResolveStatus(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody InquiryResolveUpdateRequest request
    ) {
        return ResponseEntity.ok(inquiryService.updateResolveStatus(userDetails.getId(), id, request.isResolved()));
    }
}
