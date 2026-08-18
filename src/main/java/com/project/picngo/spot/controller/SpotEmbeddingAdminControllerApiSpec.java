package com.project.picngo.spot.controller;

import com.project.picngo.spot.service.SpotEmbeddingBackfillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 의미 검색용 임베딩 관리 API 명세 (Swagger Spec).
 * SecurityConfig 규칙에 따라 ROLE_ADMIN 권한이 필요합니다.
 */
@Tag(name = "관리자 - 임베딩", description = "의미 검색용 AI 임베딩 백필 및 재계산 관리 API (ADMIN 권한 필요)")
@SecurityRequirement(name = "bearerAuth")
public interface SpotEmbeddingAdminControllerApiSpec {

    @Operation(
            summary = "임베딩 현황 조회",
            description = "검색 대상 스팟 중 의미 검색용 임베딩이 채워진 비율(커버리지)과 채워진/비어있는 스팟 개수를 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "임베딩 현황 조회 성공",
                    content = @Content(schema = @Schema(implementation = SpotEmbeddingBackfillService.EmbeddingCoverage.class))
            ),
            @ApiResponse(responseCode = "401", description = "인증 실패 (JWT 토큰 누락 또는 유효하지 않음)", content = @Content),
            @ApiResponse(responseCode = "403", description = "권한 부족 (ADMIN 권한 없음)", content = @Content)
    })
    ResponseEntity<SpotEmbeddingBackfillService.EmbeddingCoverage> getCoverage();

    @Operation(
            summary = "임베딩 일괄 백필",
            description = "임베딩이 비어 있는 스팟을 일괄로 계산하여 DB에 채웁니다. 이미 임베딩이 존재하는 스팟은 건너뜁니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "임베딩 일괄 백필 성공",
                    content = @Content(schema = @Schema(implementation = SpotEmbeddingBackfillService.BackfillResult.class))
            ),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content),
            @ApiResponse(responseCode = "403", description = "권한 부족 (ADMIN 권한 없음)", content = @Content)
    })
    ResponseEntity<SpotEmbeddingBackfillService.BackfillResult> backfill();

    @Operation(
            summary = "스팟 단건 임베딩 재계산",
            description = "특정 스팟의 임베딩이 이미 있어도 새로 외부 API로 계산해 덮어씁니다. 스팟의 이름/주소가 변경되었을 때 즉시 반영용으로 사용합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "스팟 임베딩 재계산 완료 (saved=true: 성공, saved=false: 외부 API/계산 실패)",
                    content = @Content(schema = @Schema(implementation = SpotEmbeddingAdminController.EmbeddingRecomputeResponse.class))
            ),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content),
            @ApiResponse(responseCode = "403", description = "권한 부족 (ADMIN 권한 없음)", content = @Content),
            @ApiResponse(responseCode = "404", description = "스팟을 찾을 수 없음", content = @Content)
    })
    ResponseEntity<SpotEmbeddingAdminController.EmbeddingRecomputeResponse> recompute(
            @Parameter(description = "임베딩을 재계산할 스팟 ID", example = "1")
            @PathVariable Long spotId
    );
}
