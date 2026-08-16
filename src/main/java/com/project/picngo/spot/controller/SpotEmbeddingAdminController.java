package com.project.picngo.spot.controller;

import com.project.picngo.spot.service.SpotEmbeddingBackfillService;
import com.project.picngo.spot.service.SpotEmbeddingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 의미 검색용 임베딩 관리. 관리자만 부를 수 있다(SecurityConfig의 /admin/** 규칙).
 *
 * <p>권한을 거는 이유는 두 가지다. 외부 API를 호출해 비용이 발생하고,
 * 스팟 전체를 훑는 작업이라 아무나 연타하면 부담이 된다.
 *
 * <p>평소 임베딩은 자동으로 채워진다 - 새 스팟은 등록 이벤트로 즉시, 내용이 바뀌어
 * 비워진 것은 새벽 배치로. 이 API는 그 사이를 사람이 메우는 수단이다:
 * 의미 검색을 처음 켤 때, 그리고 스팟을 고친 뒤 새벽까지 기다리지 않고 반영할 때.
 */
@Tag(name = "관리자 - 임베딩", description = "의미 검색용 임베딩 관리 API")
@RestController
@RequestMapping("/admin/embeddings")
@RequiredArgsConstructor
public class SpotEmbeddingAdminController {

    private final SpotEmbeddingBackfillService backfillService;
    private final SpotEmbeddingService spotEmbeddingService;

    @Operation(summary = "임베딩 현황 조회", description = "검색 대상 스팟 중 임베딩이 채워진 비율")
    @GetMapping
    public ResponseEntity<SpotEmbeddingBackfillService.EmbeddingCoverage> getCoverage() {
        return ResponseEntity.ok(backfillService.coverage());
    }

    @Operation(summary = "임베딩 일괄 백필",
            description = "임베딩이 비어 있는 스팟을 채운다. 이미 채워진 스팟은 건너뛴다.")
    @PostMapping("/backfill")
    public ResponseEntity<SpotEmbeddingBackfillService.BackfillResult> backfill() {
        return ResponseEntity.ok(backfillService.backfillMissingEmbeddings());
    }

    @Operation(summary = "스팟 하나 임베딩 재계산",
            description = "이미 임베딩이 있어도 새로 덮어쓴다. 스팟 내용을 고친 뒤 사용한다.")
    @PostMapping("/spots/{spotId}")
    public ResponseEntity<EmbeddingRecomputeResponse> recompute(@PathVariable Long spotId) {
        boolean saved = spotEmbeddingService.recompute(spotId);
        return ResponseEntity.ok(new EmbeddingRecomputeResponse(spotId, saved));
    }

    /**
     * @param saved 외부 API 호출까지 성공해 실제로 저장했으면 true.
     *              false면 스팟은 있는데 임베딩 계산에 실패한 것이다(API 키·외부 API 확인).
     */
    public record EmbeddingRecomputeResponse(Long spotId, boolean saved) {
    }
}
