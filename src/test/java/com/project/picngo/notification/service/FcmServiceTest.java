package com.project.picngo.notification.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.NotificationErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FcmServiceTest {

    @Mock
    private FirebaseMessaging firebaseMessaging;

    @InjectMocks
    private FcmService fcmService;

    private FirebaseMessagingException exceptionWith(MessagingErrorCode code) {
        FirebaseMessagingException ex = mock(FirebaseMessagingException.class);
        when(ex.getMessagingErrorCode()).thenReturn(code);
        return ex;
    }

    @Test
    @DisplayName("UNREGISTERED(만료/재발급) 토큰은 FCM_TOKEN_INVALID로 구분해 던진다")
    void unregisteredMapsToTokenInvalid() throws Exception {
        FirebaseMessagingException ex = exceptionWith(MessagingErrorCode.UNREGISTERED);
        when(firebaseMessaging.send(any(Message.class))).thenThrow(ex);

        assertThatThrownBy(() -> fcmService.sendMessage("dead-token", "t", "b", "/dl", 1L))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(NotificationErrorCode.FCM_TOKEN_INVALID));
    }

    @Test
    @DisplayName("INVALID_ARGUMENT(형식 오류) 토큰도 FCM_TOKEN_INVALID로 구분해 던진다")
    void invalidArgumentMapsToTokenInvalid() throws Exception {
        FirebaseMessagingException ex = exceptionWith(MessagingErrorCode.INVALID_ARGUMENT);
        when(firebaseMessaging.send(any(Message.class))).thenThrow(ex);

        assertThatThrownBy(() -> fcmService.sendMessage("bad-token", "t", "b", "/dl", 1L))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(NotificationErrorCode.FCM_TOKEN_INVALID));
    }

    @Test
    @DisplayName("그 외 FCM 오류(UNAVAILABLE 등)는 FCM_SEND_FAILED로 던진다")
    void otherErrorMapsToSendFailed() throws Exception {
        FirebaseMessagingException ex = exceptionWith(MessagingErrorCode.UNAVAILABLE);
        when(firebaseMessaging.send(any(Message.class))).thenThrow(ex);

        assertThatThrownBy(() -> fcmService.sendMessage("token", "t", "b", "/dl", 1L))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(NotificationErrorCode.FCM_SEND_FAILED));
    }

    @Test
    @DisplayName("토큰이 비어있으면 FCM_TOKEN_NOT_FOUND로 던진다 (발송 시도 자체를 안 함)")
    void emptyTokenMapsToNotFound() {
        assertThatThrownBy(() -> fcmService.sendMessage("", "t", "b", "/dl", 1L))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(NotificationErrorCode.FCM_TOKEN_NOT_FOUND));
    }
}
