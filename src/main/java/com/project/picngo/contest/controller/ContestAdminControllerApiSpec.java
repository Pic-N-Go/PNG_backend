package com.project.picngo.contest.controller;

import com.project.picngo.auth.service.CustomUserDetails;
import com.project.picngo.contest.dto.AdminContestDetailResponse;
import com.project.picngo.contest.dto.AdminContestEntryResponse;
import com.project.picngo.contest.dto.AdminContestReportResponse;
import com.project.picngo.contest.dto.AdminContestSummaryResponse;
import com.project.picngo.contest.dto.ContestCreateRequest;
import com.project.picngo.contest.dto.ContestResponse;
import com.project.picngo.contest.dto.ContestUpdateRequest;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Tag(name = "콘테스트 관리 (Admin)", description = "관리자용 콘테스트 운영 API (회차 개설, 목록, 출품작/신고 관리, 알림 발송)")
public interface ContestAdminControllerApiSpec {

    @Operation(
            summary = "콘테스트 테마 대표 사진 업로드",
            description = "콘테스트 테마 대표 사진을 S3에 업로드하고 이미지 키 및 Presigned URL을 반환합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    ResponseEntity<Map<String, String>> uploadThemeImage(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails adminUserDetails,
            @Parameter(description = "업로드할 테마 사진 파일") @RequestPart("image") MultipartFile image
    );

    @Operation(
            summary = "콘테스트 회차 개설",
            description = """
                    테마를 받아 새 회차를 만듭니다. 기간은 규칙에서 파생되며 요청으로 지정할 수 없습니다.
                    직전 회차의 발표 시각에 이어 붙어 집계 중 구간과 겹치지 않게 합니다.
                    """,
            security = @SecurityRequirement(name = "bearerAuth")
    )
    ResponseEntity<ContestResponse> createContest(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails adminUserDetails,
            @Valid @RequestBody ContestCreateRequest request
    );

    @Operation(
            summary = "콘테스트 정보 및 일정 수정",
            description = "콘테스트의 제목, 설명, 대표 이미지 및 시작 일정을 수정합니다. 시작 일정은 출품 시작 전(UPCOMING)일 때만 수정 가능하며, 4주 일정이 자동 재계산됩니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    ResponseEntity<AdminContestDetailResponse> updateContest(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails adminUserDetails,
            @Parameter(description = "콘테스트 ID", example = "1") @PathVariable Long contestId,
            @Valid @RequestBody ContestUpdateRequest request
    );

    @Operation(
            summary = "콘테스트 전체 목록 조회",
            description = "관리자용 콘테스트 전체 목록을 최신순으로 페이징 조회합니다. 각 회차의 진행 상태와 출품/투표 수, 알림 발송 여부가 포함됩니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    ResponseEntity<Page<AdminContestSummaryResponse>> getAdminContests(
            @Parameter(description = "페이지 번호 (0부터 시작)", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기", example = "20") @RequestParam(defaultValue = "20") int size
    );

    @Operation(
            summary = "콘테스트 상세 조회",
            description = "관리자용 특정 콘테스트의 상세 정보 및 통계(구독자 수, 출품 수, 참여자 수, 총 투표 수)를 조회합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    ResponseEntity<AdminContestDetailResponse> getAdminContestDetail(
            @Parameter(description = "콘테스트 ID", example = "1") @PathVariable Long contestId
    );

    @Operation(
            summary = "콘테스트 출품 시작 알림 수동 발송",
            description = "해당 콘테스트의 구독자들에게 출품 시작 푸시/인앱 알림을 즉시 발송합니다. 발송 성공 시 중복 발송 플래그가 처리됩니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    ResponseEntity<Map<String, Object>> sendStartNotification(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails adminUserDetails,
            @Parameter(description = "콘테스트 ID", example = "1") @PathVariable Long contestId
    );

    @Operation(
            summary = "콘테스트 강제 마감 및 즉시 결과 발표",
            description = "콘테스트를 즉시 마감하고 ENDED 상태로 전환하여 결과를 공개합니다. 참여자/투표자/구독자 전원에게 결과 발표 푸시 알림이 발송되며 감사 로그가 기록됩니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    ResponseEntity<AdminContestDetailResponse> publishResult(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails adminUserDetails,
            @Parameter(description = "콘테스트 ID", example = "1") @PathVariable Long contestId
    );

    @Operation(
            summary = "콘테스트 결과 발표 알림 수동 발송",
            description = "해당 콘테스트의 참여자/투표자/구독자들에게 결과 발표 푸시 알림을 수동으로 발송합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    ResponseEntity<Map<String, Object>> sendResultNotification(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails adminUserDetails,
            @Parameter(description = "콘테스트 ID", example = "1") @PathVariable Long contestId
    );

    @Operation(
            summary = "특정 콘테스트 출품작 목록 조회",
            description = "특정 콘테스트의 출품작 목록을 페이징 조회합니다. 출품작별 신고 접수 건수가 함께 반환됩니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    ResponseEntity<Page<AdminContestEntryResponse>> getAdminContestEntries(
            @Parameter(description = "콘테스트 ID", example = "1") @PathVariable Long contestId,
            @Parameter(description = "페이지 번호 (0부터 시작)", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기", example = "20") @RequestParam(defaultValue = "20") int size
    );

    @Operation(
            summary = "부적절한 출품작 관리자 강제 삭제",
            description = "부적절한 출품작을 관리자 권한으로 강제 삭제합니다. 연관된 투표 및 신고 내역, 스냅샷, S3 사진이 함께 정리되며 감사 로그가 남습니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    ResponseEntity<Void> adminDeleteEntry(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails adminUserDetails,
            @Parameter(description = "출품작 ID", example = "10") @PathVariable Long entryId,
            @Parameter(description = "삭제 사유", example = "타인의 사진 도용 및 부적절한 이미지") @RequestParam(required = false, defaultValue = "운영자 권한 강제 삭제") String reason
    );

    @Operation(
            summary = "접수된 출품작 신고 목록 조회",
            description = "유저들이 접수한 출품작 신고 목록을 최신순으로 페이징 조회합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    ResponseEntity<Page<AdminContestReportResponse>> getAdminReports(
            @Parameter(description = "페이지 번호 (0부터 시작)", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기", example = "20") @RequestParam(defaultValue = "20") int size
    );
}
