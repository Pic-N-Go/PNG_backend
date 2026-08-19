package com.project.picngo.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatMessageSendRequest(
        @NotBlank
        @Size(max = 1000)
        String content
) {
}
