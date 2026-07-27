package com.project.picngo.notification.controller;

import com.project.picngo.notification.dto.FcmTokenRequest;
import com.project.picngo.notification.dto.NotificationResponse;
import com.project.picngo.notification.dto.NotificationSettingUpdateRequest;
import com.project.picngo.notification.dto.NotificationTestRequest;
import com.project.picngo.notification.service.NotificationScheduler;
import com.project.picngo.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.project.picngo.auth.service.CustomUserDetails;
import java.util.List;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController implements NotificationControllerApiSpec {

    private final NotificationService notificationService;
    private final NotificationScheduler notificationScheduler;

    @GetMapping
    public ResponseEntity<List<NotificationResponse>> getNotifications(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(notificationService.getNotifications(userDetails.getId()));
    }

    @PostMapping("/token")
    public ResponseEntity<Void> updateFcmToken(@AuthenticationPrincipal CustomUserDetails userDetails, @RequestBody FcmTokenRequest request) {
        notificationService.updateFcmToken(userDetails.getId(), request.token());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable Long id) {
        notificationService.markAsRead(id, userDetails.getId());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead(@AuthenticationPrincipal CustomUserDetails userDetails) {
        notificationService.markAllAsRead(userDetails.getId());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/settings")
    public ResponseEntity<com.project.picngo.notification.dto.NotificationSettingResponse> getSettings(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(notificationService.getSettings(userDetails.getId()));
    }

    @PutMapping("/settings")
    public ResponseEntity<Void> updateSettings(@AuthenticationPrincipal CustomUserDetails userDetails, @RequestBody NotificationSettingUpdateRequest request) {
        notificationService.updateSettings(userDetails.getId(), request);
        return ResponseEntity.ok().build();
    }

    // TODO: 운영 배포 전 또는 프론트엔드 알림 테스트 완료 후 테스트용 알림 발송 및 스케줄러 강제 실행 API 삭제 필요
    @PostMapping("/test")
    public ResponseEntity<Void> sendTestNotification(@AuthenticationPrincipal CustomUserDetails userDetails, @RequestBody(required = false) NotificationTestRequest request) {
        notificationService.sendTestPushNotification(userDetails.getId(), request);
        return ResponseEntity.ok().build();
    }

    private final com.project.picngo.user.repository.UserRepository userRepository;
    private final com.project.picngo.auth.service.JwtTokenProvider jwtTokenProvider;

    @PostMapping("/test/scheduler")
    public ResponseEntity<Void> triggerScheduler() {
        notificationScheduler.triggerAllSchedulersManually();
        return ResponseEntity.ok().build();
    }

    @GetMapping("/test/tokens")
    public ResponseEntity<String> getTestTokens() {
        StringBuilder sb = new StringBuilder("token\n");
        userRepository.findAll().stream()
                .limit(100)
                .forEach(user -> {
                    String token = jwtTokenProvider.createAccessToken(user);
                    sb.append(token).append("\n");
                });
        return ResponseEntity.ok(sb.toString());
    }
}
