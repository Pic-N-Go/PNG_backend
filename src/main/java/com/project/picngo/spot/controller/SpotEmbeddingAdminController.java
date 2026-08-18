package com.project.picngo.spot.controller;

import com.project.picngo.admin.audit.domain.AdminActionType;
import com.project.picngo.admin.audit.service.AdminAuditLogService;
import com.project.picngo.auth.service.CustomUserDetails;
import com.project.picngo.spot.service.SpotEmbeddingBackfillService;
import com.project.picngo.spot.service.SpotEmbeddingService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 의미 검색용 임베딩 관리. 관리자만 부를 수 있다(SecurityConfig의 /admin/** 규칙).
 */
@Slf4j
@RestController
@RequestMapping("/admin/embeddings")
@RequiredArgsConstructor
public class SpotEmbeddingAdminController implements SpotEmbeddingAdminControllerApiSpec {

    private final SpotEmbeddingBackfillService backfillService;
    private final SpotEmbeddingService spotEmbeddingService;
    private final AdminAuditLogService adminAuditLogService;

    @Override
    @GetMapping
    public ResponseEntity<SpotEmbeddingBackfillService.EmbeddingCoverage> getCoverage() {
        return ResponseEntity.ok(backfillService.coverage());
    }

    @Override
    @PostMapping("/backfill")
    public ResponseEntity<SpotEmbeddingBackfillService.BackfillResult> backfill(
            @AuthenticationPrincipal CustomUserDetails adminUserDetails
    ) {
        Long adminId = adminUserDetails != null ? adminUserDetails.getId() : null;
        SpotEmbeddingBackfillService.BackfillResult result = backfillService.backfillMissingEmbeddings();

        try {
            adminAuditLogService.record(
                    adminId,
                    AdminActionType.EMBEDDING_BACKFILL,
                    "SPOT_EMBEDDING",
                    "ALL_MISSING",
                    String.format("미임베딩 스팟 일괄 백필 실행 (성공: %d, 실패: %d)", result.saved(), result.failed()),
                    null
            );
        } catch (Exception e) {
            log.warn("임베딩 백필 감사 로그 기록 실패: {}", e.getMessage());
        }

        return ResponseEntity.ok(result);
    }

    @Override
    @PostMapping("/spots/{spotId}")
    public ResponseEntity<EmbeddingRecomputeResponse> recompute(
            @AuthenticationPrincipal CustomUserDetails adminUserDetails,
            @PathVariable Long spotId
    ) {
        Long adminId = adminUserDetails != null ? adminUserDetails.getId() : null;
        boolean saved = spotEmbeddingService.recompute(spotId);

        try {
            adminAuditLogService.record(
                    adminId,
                    AdminActionType.EMBEDDING_RECALCULATE,
                    "SPOT",
                    String.valueOf(spotId),
                    String.format("스팟 [#%d] 임베딩 개별 재계산 (결과: %s)", spotId, saved ? "성공" : "실패"),
                    null
            );
        } catch (Exception e) {
            log.warn("스팟 임베딩 재계산 감사 로그 기록 실패: {}", e.getMessage());
        }

        return ResponseEntity.ok(new EmbeddingRecomputeResponse(spotId, saved));
    }

    /**
     * @param saved 외부 API 호출까지 성공해 실제로 저장했으면 true.
     *              false면 스팟은 있는데 임베딩 계산에 실패한 것이다(API 키·외부 API 확인).
     */
    public record EmbeddingRecomputeResponse(Long spotId, boolean saved) {
    }
}
