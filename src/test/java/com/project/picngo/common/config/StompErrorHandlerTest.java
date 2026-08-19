package com.project.picngo.common.config;

import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.AuthErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

import static org.assertj.core.api.Assertions.assertThat;

class StompErrorHandlerTest {

    private final StompErrorHandler errorHandler = new StompErrorHandler();

    @Test
    @DisplayName("중첩된 Access Token 만료 예외를 STOMP ERROR 코드로 전달한다")
    void exposesExpiredAccessTokenCode() {
        RuntimeException exception = new RuntimeException(
                "clientInboundChannel failure",
                new CustomException(AuthErrorCode.ACCESS_TOKEN_EXPIRED)
        );

        Message<byte[]> result = errorHandler.handleClientMessageProcessingError(null, exception);
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(result);

        assertThat(accessor.getMessage()).isEqualTo("ACCESS_TOKEN_EXPIRED");
        assertThat(accessor.getFirstNativeHeader("code")).isEqualTo("ACCESS_TOKEN_EXPIRED");
    }
}
