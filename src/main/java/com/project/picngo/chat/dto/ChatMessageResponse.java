package com.project.picngo.chat.dto;

import com.project.picngo.chat.domain.ChatMessage;
import com.project.picngo.chat.domain.ChatMessageType;

import java.time.LocalDateTime;

public record ChatMessageResponse(
        Long id,
        Long senderId,
        String senderNickname,
        ChatMessageType type,
        String content,
        LocalDateTime createdAt
) {
    public static ChatMessageResponse from(ChatMessage message) {
        return new ChatMessageResponse(
                message.getId(),
                message.getSenderId(),
                message.getSenderNickname(),
                message.getType(),
                message.getContent(),
                message.getCreatedAt()
        );
    }
}
