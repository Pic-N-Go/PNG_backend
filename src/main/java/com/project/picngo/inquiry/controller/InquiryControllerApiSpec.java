package com.project.picngo.inquiry.controller;

import com.project.picngo.auth.service.CustomUserDetails;
import com.project.picngo.inquiry.dto.InquiryCreateRequest;
import com.project.picngo.inquiry.dto.InquiryResolveUpdateRequest;
import com.project.picngo.inquiry.dto.InquiryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "1:1 문의 (Inquiry)", description = "사용자 1:1 문의 등록, 목록 조회, 상세 조회 및 해결 상태 변경 API")
@SecurityRequirement(name = "bearerAuth")
public interface InquiryControllerApiSpec {

    @Operation(summary = "1:1 문의 등록", description = "새로운 1:1 문의를 등록합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "문의 등록 성공", content = @Content(schema = @Schema(implementation = InquiryResponse.class))),
            @ApiResponse(responseCode = "400", description = "유효하지 않은 입력 데이터", content = @Content),
            @ApiResponse(responseCode = "401", description = "인증 실패 (로그인 필요)", content = @Content)
    })
    ResponseEntity<InquiryResponse> createInquiry(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody InquiryCreateRequest request
    );

    @Operation(summary = "내가 작성한 1:1 문의 목록 조회", description = "현재 로그인한 사용자가 작성한 1:1 문의 목록을 최신순으로 페이징 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "문의 목록 조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패 (로그인 필요)", content = @Content)
    })
    ResponseEntity<Page<InquiryResponse>> getMyInquiries(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "페이지 번호 (0부터 시작)", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기", example = "20") @RequestParam(defaultValue = "20") int size
    );

    @Operation(summary = "1:1 문의 상세 조회", description = "특정 1:1 문의의 상세 내용을 조회합니다. (작성자 본인 또는 관리자만 조회 가능)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "문의 상세 조회 성공", content = @Content(schema = @Schema(implementation = InquiryResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패 (로그인 필요)", content = @Content),
            @ApiResponse(responseCode = "403", description = "접근 권한 없음 (타인의 문의 조회 시도)", content = @Content),
            @ApiResponse(responseCode = "404", description = "문의를 찾을 수 없음", content = @Content)
    })
    ResponseEntity<InquiryResponse> getInquiryDetail(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "문의 ID", example = "1") @PathVariable Long id
    );

    @Operation(summary = "1:1 문의 해결 상태 변경", description = "사용자가 자신의 문의에 대해 해결 여부(isResolved) 상태를 변경합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "해결 상태 변경 성공", content = @Content(schema = @Schema(implementation = InquiryResponse.class))),
            @ApiResponse(responseCode = "400", description = "유효하지 않은 입력 데이터", content = @Content),
            @ApiResponse(responseCode = "401", description = "인증 실패 (로그인 필요)", content = @Content),
            @ApiResponse(responseCode = "403", description = "접근 권한 없음 (타인의 문의 수정 시도)", content = @Content),
            @ApiResponse(responseCode = "404", description = "문의를 찾을 수 없음", content = @Content)
    })
    ResponseEntity<InquiryResponse> updateResolveStatus(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "문의 ID", example = "1") @PathVariable Long id,
            @Valid @RequestBody InquiryResolveUpdateRequest request
    );
}
