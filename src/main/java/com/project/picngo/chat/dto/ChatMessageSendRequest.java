package com.project.picngo.chat.dto;

import jakarta.validation.constraints.NotBlank;

public record ChatMessageSendRequest(
        @NotBlank
        String content
) {
}
