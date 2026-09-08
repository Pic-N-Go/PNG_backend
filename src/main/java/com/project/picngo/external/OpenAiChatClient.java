package com.project.picngo.external;

import com.project.picngo.external.dto.OpenAiChatRequest;
import com.project.picngo.external.dto.OpenAiChatResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Component
public class OpenAiChatClient {

    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(12);

    private final WebClient webClient;
    private final String apiKey;
    private final String model;

    public OpenAiChatClient(
            WebClient.Builder webClientBuilder,
            @Value("${picngo.llm.api-key:}") String apiKey,
            @Value("${picngo.llm.model:gpt-4o-mini}") String model,
            @Value("${picngo.llm.base-url:https://api.openai.com/v1/chat/completions}") String baseUrl) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
        this.model = model;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * JSON 모드로 LLM 응답을 요청합니다.
     *
     * @param systemPrompt 시스템 지침 (JSON 스키마 정의 포함)
     * @param userPrompt   사용자 요청 또는 컨텍스트
     * @param maxTokens    최대 토큰 수
     * @return 파싱된 JSON 문자열 (실패 시 Optional.empty())
     */
    public Optional<String> chatJson(String systemPrompt, String userPrompt, int maxTokens) {
        if (!isConfigured()) {
            log.debug("OpenAI API 키가 설정되지 않아 chatJson 호출을 건너뜁니다.");
            return Optional.empty();
        }

        try {
            OpenAiChatRequest request = OpenAiChatRequest.ofJson(model, systemPrompt, userPrompt, 0.2, maxTokens);

            OpenAiChatResponse response = webClient.post()
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(OpenAiChatResponse.class)
                    .timeout(CALL_TIMEOUT)
                    .block();

            if (response == null || response.firstContent() == null) {
                log.warn("OpenAI Chat 응답이 비어 있습니다.");
                return Optional.empty();
            }

            if (response.usage() != null) {
                log.info("OpenAI Chat 토큰 사용량: prompt={}, completion={}, total={}",
                        response.usage().promptTokens(), response.usage().completionTokens(), response.usage().totalTokens());
            }

            return Optional.of(response.firstContent());

        } catch (WebClientResponseException e) {
            log.warn("OpenAI Chat API HTTP 에러: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            return Optional.empty();
        } catch (Exception e) {
            log.warn("OpenAI Chat API 호출 실패: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
