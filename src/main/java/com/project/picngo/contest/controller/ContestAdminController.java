package com.project.picngo.contest.controller;

import com.project.picngo.admin.audit.domain.AdminActionType;
import com.project.picngo.admin.audit.service.AdminAuditLogService;
import com.project.picngo.auth.service.CustomUserDetails;
import com.project.picngo.contest.dto.ContestCreateRequest;
import com.project.picngo.contest.dto.ContestResponse;
import com.project.picngo.contest.service.ContestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 콘테스트 회차 개설. 관리자만 부를 수 있다(SecurityConfig의 /admin/** 규칙).
 *
 * 지금까지는 contest 행을 손으로 INSERT하는 수밖에 없었고, 그러면 출품·투표 기간이
 * 매번 사람 손에 달려 규칙이 강제되지 않았다.
 */
@Slf4j
@RestController
@RequestMapping("/admin/contests")
@RequiredArgsConstructor
public class ContestAdminController implements ContestAdminControllerApiSpec {

    private final ContestService contestService;
    private final AdminAuditLogService adminAuditLogService;

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
}
