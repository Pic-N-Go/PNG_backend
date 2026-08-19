package com.project.picngo.admin.audit.controller;

import com.project.picngo.admin.audit.domain.AdminActionType;
import com.project.picngo.admin.audit.dto.AdminAuditLogResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;

@Tag(name = "Admin Audit API", description = "관리자 주요 활동 감사 로그(Audit Log) 조회 API (ROLE_ADMIN 전용)")
public interface AdminAuditLogControllerApiSpec {

    @Operation(summary = "관리자 감사 로그 목록 페이징 및 필터 조회", description = "관리자가 수행한 회원 권한 변경, 1:1 문의 답변, 임베딩 재계산 등의 감사 로그를 페이징 및 필터 검색합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공", content = @Content(schema = @Schema(implementation = Page.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패 (JWT 토큰 누락 또는 유효하지 않음)"),
            @ApiResponse(responseCode = "403", description = "접근 권한 없음 (ROLE_ADMIN 전용)")
    })
    ResponseEntity<Page<AdminAuditLogResponse>> getAuditLogs(
            @Parameter(description = "특정 관리자 회원 ID 필터", example = "1") Long adminUserId,
            @Parameter(description = "작업 유형 (ROLE_UPDATE, INQUIRY_ANSWER, EMBEDDING_RECALCULATE, EMBEDDING_BACKFILL, TOUR_API_SYNC)") AdminActionType actionType,
            @Parameter(description = "페이지 번호 (0부터 시작)", example = "0") int page,
            @Parameter(description = "페이지 크기", example = "20") int size
    );
}
