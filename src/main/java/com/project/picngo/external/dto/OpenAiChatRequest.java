package com.project.picngo.external.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpenAiChatRequest(
        String model,
        List<Message> messages,
        @JsonProperty("response_format")
        Map<String, String> responseFormat,
        Double temperature,
        @JsonProperty("max_tokens")
        Integer maxTokens
) {
    public record Message(String role, String content) {}

    public static OpenAiChatRequest ofJson(String model, String systemPrompt, String userPrompt, double temperature, int maxTokens) {
        return new OpenAiChatRequest(
                model,
                List.of(
                        new Message("system", systemPrompt),
                        new Message("user", userPrompt)
                ),
                Map.of("type", "json_object"),
                temperature,
                maxTokens
        );
    }
}
