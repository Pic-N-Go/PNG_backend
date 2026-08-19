package com.project.picngo.chat.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatMessageSendRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("메시지는 1000자까지 허용한다")
    void acceptsMessageWithMaximumLength() {
        ChatMessageSendRequest request = new ChatMessageSendRequest("가".repeat(1000));

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    @DisplayName("메시지가 1000자를 초과하면 거부한다")
    void rejectsMessageOverMaximumLength() {
        ChatMessageSendRequest request = new ChatMessageSendRequest("가".repeat(1001));

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    @DisplayName("공백 메시지는 거부한다")
    void rejectsBlankMessage() {
        ChatMessageSendRequest request = new ChatMessageSendRequest("   ");

        assertFalse(validator.validate(request).isEmpty());
    }
}
