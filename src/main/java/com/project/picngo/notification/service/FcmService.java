package com.project.picngo.notification.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.NotificationErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class FcmService {

    private final FirebaseMessaging firebaseMessaging;

    public void sendMessage(String targetToken, String title, String body, String deepLink) {
        sendMessage(targetToken, title, body, deepLink, null);
    }

    public void sendMessage(String targetToken, String title, String body, String deepLink, Long spotId) {
        if (targetToken == null || targetToken.isEmpty()) {
            log.warn("FCM Token이 없어 알림을 보낼 수 없습니다.");
            throw new CustomException(NotificationErrorCode.FCM_TOKEN_NOT_FOUND);
        }

        Notification notification = Notification.builder()
                .setTitle(title)
                .setBody(body)
                .build();

        Message.Builder messageBuilder = Message.builder()
                .setToken(targetToken)
                .setNotification(notification);

        if (deepLink != null && !deepLink.isEmpty()) {
            messageBuilder.putData("deepLink", deepLink);
        }
        if (spotId != null) {
            messageBuilder.putData("spotId", String.valueOf(spotId));
        }

        try {
            String response = firebaseMessaging.send(messageBuilder.build());
            log.info("FCM 알림 발송 성공: {}", response);
        } catch (Exception e) {
            log.error("FCM 알림 발송 실패", e);
            throw new CustomException(NotificationErrorCode.FCM_SEND_FAILED);
        }
    }
}
