package com.project.picngo.notification.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
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
        } catch (FirebaseMessagingException e) {
            MessagingErrorCode code = e.getMessagingErrorCode();
            // UNREGISTERED는 토큰이 확실히 무효(앱 삭제/만료/미접속)이므로 항상 정리 대상.
            // INVALID_ARGUMENT는 토큰 형식 오류일 수도, 메시지 페이로드 오류일 수도 있어 —
            // 응답이 '등록 토큰'을 지목할 때만 토큰 무효로 보고, 그 외(페이로드 문제)엔 정상 토큰을 보존한다.
            if (code == MessagingErrorCode.UNREGISTERED || isRegistrationTokenError(code, e)) {
                log.warn("무효 FCM 토큰 감지 (code: {}) → 토큰 정리 대상", code);
                throw new CustomException(NotificationErrorCode.FCM_TOKEN_INVALID);
            }
            log.error("FCM 알림 발송 실패 (code: {})", code, e);
            throw new CustomException(NotificationErrorCode.FCM_SEND_FAILED);
        } catch (Exception e) {
            log.error("FCM 알림 발송 실패", e);
            throw new CustomException(NotificationErrorCode.FCM_SEND_FAILED);
        }
    }

    // INVALID_ARGUMENT 응답 중 '등록 토큰이 유효하지 않다'는 경우만 토큰 무효로 판별한다.
    // (메시지 페이로드 오류로 인한 INVALID_ARGUMENT에 정상 토큰이 삭제되는 것을 방지)
    private boolean isRegistrationTokenError(MessagingErrorCode code, FirebaseMessagingException e) {
        if (code != MessagingErrorCode.INVALID_ARGUMENT) {
            return false;
        }
        String message = e.getMessage();
        return message != null && message.toLowerCase().contains("registration token");
    }
}
