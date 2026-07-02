package com.project.picngo.chat.dto;

public record ChatParticipantResponse(
        Long userId,
        String nickname
) {
    public static ChatParticipantResponse from(String value) {
        String[] parts = value.split(":", 2);

        return new ChatParticipantResponse(
                Long.valueOf(parts[0]),
                parts.length > 1 ? parts[1] : ""
        );
    }
}
