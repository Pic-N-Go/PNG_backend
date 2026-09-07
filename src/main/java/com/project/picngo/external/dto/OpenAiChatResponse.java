package com.project.picngo.external.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenAiChatResponse(
        String id,
        List<Choice> choices,
        Usage usage
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(
            int index,
            Message message,
            @JsonProperty("finish_reason") String finishReason
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(
            String role,
            String content
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Usage(
            @JsonProperty("prompt_tokens") int promptTokens,
            @JsonProperty("completion_tokens") int completionTokens,
            @JsonProperty("total_tokens") int totalTokens
    ) {}

    public String firstContent() {
        if (choices == null || choices.isEmpty() || choices.get(0).message() == null) {
            return null;
        }
        return choices.get(0).message().content();
    }
}
