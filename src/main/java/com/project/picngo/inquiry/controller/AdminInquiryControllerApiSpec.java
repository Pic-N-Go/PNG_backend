package com.project.picngo.inquiry.controller;

import com.project.picngo.auth.service.CustomUserDetails;
import com.project.picngo.inquiry.domain.InquiryStatus;
import com.project.picngo.inquiry.dto.AdminInquiryAnswerRequest;
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

@Tag(name = "관리자 - 1:1 문의", description = "관리자 전용 전체 1:1 문의 조회 및 답변 작성 API (ADMIN 권한 필요)")
@SecurityRequirement(name = "bearerAuth")
public interface AdminInquiryControllerApiSpec {

    @Operation(summary = "관리자용 전체 1:1 문의 목록 및 검색 조회", description = "전체 1:1 문의 목록을 최신순으로 페이징 조회합니다. 문의 상태(PENDING/ANSWERED/RESOLVED), 해결 여부(isResolved), 검색어(제목/내용/작성자닉네임/이메일) 필터링이 가능합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "문의 목록 조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패 (로그인 필요)", content = @Content),
            @ApiResponse(responseCode = "403", description = "권한 부족 (ADMIN 권한 없음)", content = @Content)
    })
    ResponseEntity<Page<InquiryResponse>> getInquiriesForAdmin(
            @Parameter(description = "문의 상태 (PENDING: 답변대기, ANSWERED: 답변완료, RESOLVED: 해결됨)", example = "PENDING") @RequestParam(required = false) InquiryStatus status,
            @Parameter(description = "해결 여부 필터 (true / false)", example = "false") @RequestParam(required = false) Boolean isResolved,
            @Parameter(description = "검색 키워드 (제목, 내용, 작성자 닉네임/이메일)", example = "코스") @RequestParam(required = false) String keyword,
            @Parameter(description = "페이지 번호 (0부터 시작)", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기", example = "20") @RequestParam(defaultValue = "20") int size
    );

    @Operation(summary = "관리자 1:1 문의 답변 등록 및 수정", description = "특정 1:1 문의에 관리자 공식 답변을 작성하거나 수정합니다. 작성 완료 시 문의 상태가 ANSWERED로 자동 변경됩니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "답변 등록 성공", content = @Content(schema = @Schema(implementation = InquiryResponse.class))),
            @ApiResponse(responseCode = "400", description = "유효하지 않은 답변 데이터", content = @Content),
            @ApiResponse(responseCode = "401", description = "인증 실패 (로그인 필요)", content = @Content),
            @ApiResponse(responseCode = "403", description = "권한 부족 (ADMIN 권한 없음)", content = @Content),
            @ApiResponse(responseCode = "404", description = "문의를 찾을 수 없음", content = @Content)
    })
    ResponseEntity<InquiryResponse> answerInquiry(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails adminUserDetails,
            @Parameter(description = "문의 ID", example = "1") @PathVariable Long id,
            @Valid @RequestBody AdminInquiryAnswerRequest request
    );
}
