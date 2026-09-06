package com.project.picngo.contest.controller;

import com.project.picngo.admin.audit.domain.AdminActionType;
import com.project.picngo.admin.audit.service.AdminAuditLogService;
import com.project.picngo.auth.service.CustomUserDetails;
import com.project.picngo.contest.dto.AdminContestDetailResponse;
import com.project.picngo.contest.dto.AdminContestEntryResponse;
import com.project.picngo.contest.dto.AdminContestReportResponse;
import com.project.picngo.contest.dto.AdminContestSummaryResponse;
import com.project.picngo.contest.dto.ContestCreateRequest;
import com.project.picngo.contest.dto.ContestResponse;
import com.project.picngo.contest.dto.ContestUpdateRequest;
import com.project.picngo.contest.service.ContestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.project.picngo.common.image.dto.ImageUploadResult;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 콘테스트 관리자 컨트롤러. SecurityConfig의 /admin/** 규칙에 의해 ADMIN 권한만 접근 가능하다.
 */
@Slf4j
@RestController
@RequestMapping("/admin/contests")
@RequiredArgsConstructor
public class ContestAdminController implements ContestAdminControllerApiSpec {

    private final ContestService contestService;
    private final AdminAuditLogService adminAuditLogService;

    @Override
    @PostMapping(value = "/theme-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> uploadThemeImage(
            @AuthenticationPrincipal CustomUserDetails adminUserDetails,
            @RequestPart("image") MultipartFile image
    ) {
        ImageUploadResult result = contestService.uploadThemeImage(image);
        return ResponseEntity.ok(Map.of(
                "key", result.key(),
                "imageUrl", result.url()
        ));
    }

    @Override
    @PostMapping
    public ResponseEntity<ContestResponse> createContest(
            @AuthenticationPrincipal CustomUserDetails adminUserDetails,
            @Valid @RequestBody ContestCreateRequest request
    ) {
        Long adminId = adminUserDetails != null ? adminUserDetails.getId() : null;
        ContestResponse response = contestService.createContest(adminId, request);

        try {
            adminAuditLogService.record(
                    adminId,
                    AdminActionType.CONTEST_CREATE,
                    "CONTEST",
                    String.valueOf(response.contestId()),
                    String.format(
                            "콘테스트 [%s] 개설 (출품 %s ~ %s, 투표 ~ %s, 발표 %s)",
                            response.title(),
                            response.submitStartAt(),
                            response.submitEndAt(),
                            response.voteEndAt(),
                            response.resultOpenAt()
                    ),
                    null
            );
        } catch (Exception e) {
            log.warn("콘테스트 개설 감사 로그 기록 실패: {}", e.getMessage());
        }

        return ResponseEntity.ok(response);
    }

    @Override
    @PatchMapping("/{contestId}")
    public ResponseEntity<AdminContestDetailResponse> updateContest(
            @AuthenticationPrincipal CustomUserDetails adminUserDetails,
            @PathVariable Long contestId,
            @Valid @RequestBody ContestUpdateRequest request
    ) {
        Long adminId = adminUserDetails != null ? adminUserDetails.getId() : null;
        AdminContestDetailResponse response = contestService.updateContest(contestId, request);

        try {
            adminAuditLogService.record(
                    adminId,
                    AdminActionType.CONTEST_UPDATE,
                    "CONTEST",
                    String.valueOf(contestId),
                    String.format("콘테스트 [%s] (ID: %d) 정보/일정 수정", response.title(), contestId),
                    null
            );
        } catch (Exception e) {
            log.warn("콘테스트 수정 감사 로그 기록 실패: {}", e.getMessage());
        }

        return ResponseEntity.ok(response);
    }

    @Override
    @GetMapping
    public ResponseEntity<Page<AdminContestSummaryResponse>> getAdminContests(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<AdminContestSummaryResponse> response = contestService.getAdminContests(PageRequest.of(page, size));
        return ResponseEntity.ok(response);
    }

    @Override
    @GetMapping("/{contestId}")
    public ResponseEntity<AdminContestDetailResponse> getAdminContestDetail(@PathVariable Long contestId) {
        AdminContestDetailResponse response = contestService.getAdminContestDetail(contestId);
        return ResponseEntity.ok(response);
    }

    @Override
    @PostMapping("/{contestId}/notifications/start")
    public ResponseEntity<Map<String, Object>> sendStartNotification(
            @AuthenticationPrincipal CustomUserDetails adminUserDetails,
            @PathVariable Long contestId
    ) {
        Long adminId = adminUserDetails != null ? adminUserDetails.getId() : null;
        int sentCount = contestService.sendStartNotification(contestId);

        try {
            adminAuditLogService.record(
                    adminId,
                    AdminActionType.CONTEST_NOTIFICATION_SEND,
                    "CONTEST",
                    String.valueOf(contestId),
                    String.format("콘테스트 (ID: %d) 출품 시작 알림 수동 발송 (수신자: %d명)", contestId, sentCount),
                    null
            );
        } catch (Exception e) {
            log.warn("콘테스트 알림 발송 감사 로그 기록 실패: {}", e.getMessage());
        }

        return ResponseEntity.ok(Map.of(
                "contestId", contestId,
                "sentCount", sentCount,
                "message", "콘테스트 시작 알림이 성공적으로 발송되었습니다."
        ));
    }

    @Override
    @PostMapping("/{contestId}/publish-result")
    public ResponseEntity<AdminContestDetailResponse> publishResult(
            @AuthenticationPrincipal CustomUserDetails adminUserDetails,
            @PathVariable Long contestId
    ) {
        Long adminId = adminUserDetails != null ? adminUserDetails.getId() : null;
        AdminContestDetailResponse response = contestService.forcePublishResult(contestId);

        try {
            adminAuditLogService.record(
                    adminId,
                    AdminActionType.CONTEST_RESULT_PUBLISH,
                    "CONTEST",
                    String.valueOf(contestId),
                    String.format("콘테스트 [%s] (ID: %d) 강제 마감 및 즉시 결과 발표", response.title(), contestId),
                    null
            );
        } catch (Exception e) {
            log.warn("콘테스트 결과 발표 감사 로그 기록 실패: {}", e.getMessage());
        }

        return ResponseEntity.ok(response);
    }

    @Override
    @PostMapping("/{contestId}/notifications/result")
    public ResponseEntity<Map<String, Object>> sendResultNotification(
            @AuthenticationPrincipal CustomUserDetails adminUserDetails,
            @PathVariable Long contestId
    ) {
        Long adminId = adminUserDetails != null ? adminUserDetails.getId() : null;
        int sentCount = contestService.sendResultNotification(contestId);

        try {
            adminAuditLogService.record(
                    adminId,
                    AdminActionType.CONTEST_RESULT_NOTIFICATION_SEND,
                    "CONTEST",
                    String.valueOf(contestId),
                    String.format("콘테스트 (ID: %d) 결과 발표 알림 수동 발송 (수신자: %d명)", contestId, sentCount),
                    null
            );
        } catch (Exception e) {
            log.warn("콘테스트 결과 알림 발송 감사 로그 기록 실패: {}", e.getMessage());
        }

        return ResponseEntity.ok(Map.of(
                "contestId", contestId,
                "sentCount", sentCount,
                "message", "콘테스트 결과 발표 알림이 성공적으로 발송되었습니다."
        ));
    }

    @Override
    @GetMapping("/{contestId}/entries")
    public ResponseEntity<Page<AdminContestEntryResponse>> getAdminContestEntries(
            @PathVariable Long contestId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<AdminContestEntryResponse> response = contestService.getAdminContestEntries(contestId, PageRequest.of(page, size));
        return ResponseEntity.ok(response);
    }

    @Override
    @DeleteMapping("/entries/{entryId}")
    public ResponseEntity<Void> adminDeleteEntry(
            @AuthenticationPrincipal CustomUserDetails adminUserDetails,
            @PathVariable Long entryId,
            @RequestParam(required = false, defaultValue = "운영자 권한 강제 삭제") String reason
    ) {
        Long adminId = adminUserDetails != null ? adminUserDetails.getId() : null;
        contestService.adminDeleteEntry(entryId, adminId, reason);

        try {
            adminAuditLogService.record(
                    adminId,
                    AdminActionType.CONTEST_ENTRY_DELETE,
                    "CONTEST_ENTRY",
                    String.valueOf(entryId),
                    String.format("출품작 (ID: %d) 관리자 강제 삭제 (사유: %s)", entryId, reason),
                    null
            );
        } catch (Exception e) {
            log.warn("출품작 강제 삭제 감사 로그 기록 실패: {}", e.getMessage());
        }

        return ResponseEntity.noContent().build();
    }

    @Override
    @GetMapping("/reports")
    public ResponseEntity<Page<AdminContestReportResponse>> getAdminReports(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<AdminContestReportResponse> response = contestService.getAdminReports(PageRequest.of(page, size));
        return ResponseEntity.ok(response);
    }
}
