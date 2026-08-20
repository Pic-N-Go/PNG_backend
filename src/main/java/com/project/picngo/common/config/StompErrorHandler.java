package com.project.picngo.common.config;

import com.project.picngo.common.exception.BaseErrorCode;
import com.project.picngo.common.exception.CustomException;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

@Component
public class StompErrorHandler extends StompSubProtocolErrorHandler {

    @Override
    public Message<byte[]> handleClientMessageProcessingError(
            @Nullable Message<byte[]> clientMessage,
            Throwable exception
    ) {
        CustomException customException = findCustomException(exception);
        if (customException == null) {
            return super.handleClientMessageProcessingError(clientMessage, exception);
        }

        BaseErrorCode errorCode = customException.getErrorCode();
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.ERROR);
        accessor.setMessage(errorCode.name());
        accessor.setNativeHeader("code", errorCode.name());

        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private CustomException findCustomException(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof CustomException customException) {
                return customException;
            }
            current = current.getCause();
        }
        return null;
    }
}
