package com.project.picngo.user.controller;

import com.project.picngo.auth.service.CustomUserDetails;
import com.project.picngo.user.dto.UserEquipmentCreateRequest;
import com.project.picngo.user.dto.UserEquipmentResponse;
import com.project.picngo.user.dto.UserEquipmentUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@Tag(name = "사용자 장비 (User Equipment)", description = "현재 로그인한 사용자의 카메라 및 렌즈 관리 API")

public interface UserEquipmentControllerApiSpec {

    @Operation(summary = "내 장비 목록 조회", description = "현재 로그인한 사용자가 등록한 카메라와 렌즈를 등록 순서대로 조회합니다.")
    ResponseEntity<List<UserEquipmentResponse>> getMyEquipments(
            @Parameter(hidden = true)
            @AuthenticationPrincipal CustomUserDetails userDetails
    );

    @Operation(summary = "내 장비 등록", description = "현재 로그인한 사용자의 카메라 또는 렌즈를 등록합니다.")
    ResponseEntity<UserEquipmentResponse> createUserEquipment(
            @Parameter(hidden = true)
            @AuthenticationPrincipal CustomUserDetails userDetails,

            @Valid @RequestBody
            UserEquipmentCreateRequest request
    );

    @Operation(summary = "내 장비 수정", description = "현재 로그인한 사용자가 소유한 장비의 종류와 이름을 수정합니다.")
    ResponseEntity<UserEquipmentResponse> updateUserEquipment(
            @Parameter(hidden = true)
            @AuthenticationPrincipal CustomUserDetails userDetails,

            @Parameter(description = "수정할 장비 ID")
            @PathVariable Long equipmentId,

            @Valid @RequestBody
            UserEquipmentUpdateRequest request
    );

    @Operation(summary = "내 장비 삭제", description = "현재 로그인한 사용자가 소유한 장비를 삭제합니다.")
    ResponseEntity<Void> deleteUserEquipment(
            @Parameter(hidden = true)
            @AuthenticationPrincipal CustomUserDetails userDetails,

            @Parameter(description = "삭제할 장비 ID")
            @PathVariable Long equipmentId
    );
}
