package com.project.picngo.notification.controller;

import com.project.picngo.notification.dto.FcmTokenRequest;
import com.project.picngo.notification.dto.NotificationResponse;
import com.project.picngo.notification.dto.NotificationSettingUpdateRequest;
import com.project.picngo.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController implements NotificationControllerApiSpec {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<List<NotificationResponse>> getNotifications(@RequestParam Long userId) {
        return ResponseEntity.ok(notificationService.getNotifications(userId));
    }

    @PostMapping("/token")
    public ResponseEntity<Void> updateFcmToken(@RequestParam Long userId, @RequestBody FcmTokenRequest request) {
        notificationService.updateFcmToken(userId, request.token());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@RequestParam Long userId, @PathVariable Long id) {
        notificationService.markAsRead(id, userId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead(@RequestParam Long userId) {
        notificationService.markAllAsRead(userId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/settings")
    public ResponseEntity<Void> updateSettings(@RequestParam Long userId, @RequestBody NotificationSettingUpdateRequest request) {
        notificationService.updateSettings(userId, request);
        return ResponseEntity.ok().build();
    }
}
