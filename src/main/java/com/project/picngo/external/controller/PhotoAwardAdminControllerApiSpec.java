package com.project.picngo.external.controller;

import com.project.picngo.auth.service.CustomUserDetails;
import com.project.picngo.spot.dto.PhotoAwardSyncStatusResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "사진공모전 동기화 (Admin Photo Award Sync)", description = "한국관광공사 사진공모전 수상작 데이터 비동기 동기화 및 관리자 API")
public interface PhotoAwardAdminControllerApiSpec {

    @Operation(
            summary = "사진공모전 수상작 특정 지역 동기화 (ADMIN 권한 필요)",
            description = "한국관광공사 PhokoAwrdService에서 특정 시/도 지역(법정동 코드 또는 TourAPI 지역코드)의 수상작 데이터를 수집하여 기존 스팟 보강 또는 신규 스팟으로 저장합니다. (POST /admin/photo-award/sync, ROLE_ADMIN 권한 필요)\n비동기 큐로 전송되어 백그라운드에서 실행됩니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    ResponseEntity<String> syncArea(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails adminUserDetails,
            @Parameter(description = "법정동 시도 코드 (11: 서울, 26: 부산, 41: 경기 등)") @RequestParam(required = false) Integer lDongRegnCd,
            @Parameter(description = "TourAPI 지역 코드 (1: 서울, 6: 부산, 31: 경기 등 - lDongRegnCd 미입력 시 자동 매핑)") @RequestParam(required = false) Integer areaCode
    );

    @Operation(
            summary = "사진공모전 수상작 전국 17개 지역 전체 동기화 (ADMIN 권한 필요)",
            description = "전국 17개 시도의 사진공모전 수상작 전체를 일괄 수집하여 백그라운드에서 동기화합니다. (POST /admin/photo-award/sync/all, ROLE_ADMIN 권한 필요)",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    ResponseEntity<String> syncAll(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails adminUserDetails
    );

    @Operation(
            summary = "사진공모전 동기화 진행 상태 및 진행률 조회 (ADMIN 권한 필요)",
            description = "현재 백그라운드에서 실행 중인 사진공모전 동기화 작업의 진행 상태, 대상 지역, 처리/신규/보강 건수, 진행률(%), 상태 메시지를 조회합니다. (GET /admin/photo-award/sync/status)",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    ResponseEntity<PhotoAwardSyncStatusResponse> getSyncStatus();
}
