package com.project.picngo.spotalert.controller;

import com.project.picngo.spotalert.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.project.picngo.auth.service.CustomUserDetails;
import java.util.List;

@Tag(name = "출사알림 (SpotAlert)", description = "사용자 찜(출사알림) 폴더 및 장소 관리 API")
public interface SpotAlertControllerApiSpec {

    @Operation(summary = "내 출사알림 목록 조회", description = "사용자가 출사알림에 담은 모든 스팟 목록과 설정을 조회합니다.")
    ResponseEntity<List<SpotAlertSettingResponse>> getSpotAlerts(@Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails);

    @Operation(summary = "출사알림 상세 설정 조회", description = "특정 스팟의 출사알림 알림 설정을 조회합니다.")
    ResponseEntity<SpotAlertSettingResponse> getSpotAlertDetail(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails, 
            @Parameter(description = "스팟 ID") @PathVariable Long spotId
    );

    @Operation(summary = "출사알림 설정 저장 (추가/수정)", description = "특정 스팟의 출사알림 설정을 저장합니다. 기존에 없으면 새로 생성하고, 있으면 덮어씁니다.")
    ResponseEntity<SpotAlertSettingResponse> updateSpotAlertSettings(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "스팟 ID") @PathVariable Long spotId,
            @RequestBody SpotAlertSettingUpdateRequest request
    );

    @Operation(summary = "출사알림에서 장소 제거", description = "특정 스팟을 출사알림에서 완전히 삭제합니다.")
    ResponseEntity<Void> deleteSpotAlert(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails, 
            @Parameter(description = "스팟 ID") @PathVariable Long spotId
    );
}
