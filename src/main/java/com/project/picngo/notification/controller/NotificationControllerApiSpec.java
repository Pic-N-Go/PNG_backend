package com.project.picngo.notification.controller;

import com.project.picngo.notification.dto.NotificationResponse;
import com.project.picngo.notification.dto.NotificationSettingUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.project.picngo.auth.service.CustomUserDetails;
import com.project.picngo.notification.dto.FcmTokenRequest;

import java.util.List;

@Tag(name = "알림 (Notification)", description = "알림 내역 조회 및 수신 설정 API")
public interface NotificationControllerApiSpec {

    @Operation(summary = "알림 목록 조회", description = "로그인한 사용자의 알림 목록을 반환합니다.")
    ResponseEntity<List<NotificationResponse>> getNotifications(@Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails);

    @Operation(summary = "FCM 토큰 등록/갱신", description = "푸시 알림 수신을 위한 기기 토큰을 저장합니다.")
    ResponseEntity<Void> updateFcmToken(@Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails, @RequestBody FcmTokenRequest request);

    @Operation(summary = "알림 단건 읽음 처리", description = "특정 알림을 읽음 상태로 변경합니다.")
    ResponseEntity<Void> markAsRead(@Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails, @Parameter(description = "알림 ID") @PathVariable Long id);

    @Operation(summary = "모든 알림 읽음 처리", description = "사용자의 안 읽은 모든 알림을 읽음 상태로 변경합니다.")
    ResponseEntity<Void> markAllAsRead(@Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails);

    @Operation(summary = "알림 수신 설정 조회", description = "로그인한 사용자의 알림 수신 설정(출사알림, 골든아워, 커뮤니티 토글 및 DND 시간)을 조회합니다.")
    ResponseEntity<com.project.picngo.notification.dto.NotificationSettingResponse> getSettings(@Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails);

    @Operation(summary = "알림 수신 설정 변경", description = "푸시 알림 수신 여부 및 방해금지 시간(DND)을 변경합니다.")
    ResponseEntity<Void> updateSettings(@Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails, @RequestBody NotificationSettingUpdateRequest request);
}
