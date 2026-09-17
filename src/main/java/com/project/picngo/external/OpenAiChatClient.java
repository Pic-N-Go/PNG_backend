package com.project.picngo.external;

import com.project.picngo.external.dto.OpenAiChatRequest;
import com.project.picngo.external.dto.OpenAiChatResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
public class OpenAiChatClient {

    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(12);

    private static final String METRIC_LLM_DURATION = "ai.llm.call.duration";
    private static final String METRIC_LLM_TOKENS = "ai.llm.tokens";
    private static final String METRIC_LLM_CALL = "ai.llm.call.count";

    private final WebClient webClient;
    private final String apiKey;
    private final String model;
    private final MeterRegistry meterRegistry;

    public OpenAiChatClient(
            WebClient.Builder webClientBuilder,
            @Value("${picngo.llm.api-key:}") String apiKey,
            @Value("${picngo.llm.model:gpt-4o-mini}") String model,
            @Value("${picngo.llm.base-url:https://api.openai.com/v1/chat/completions}") String baseUrl,
            @Autowired(required = false) MeterRegistry meterRegistry) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
        this.model = model;
        this.meterRegistry = meterRegistry;
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

        Timer.Sample sample = (meterRegistry != null) ? Timer.start(meterRegistry) : null;
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
                recordLlmMetrics(sample, "json", "empty", null);
                return Optional.empty();
            }

            if (response.usage() != null) {
                log.info("OpenAI Chat 토큰 사용량: prompt={}, completion={}, total={}",
                        response.usage().promptTokens(), response.usage().completionTokens(), response.usage().totalTokens());
            }

            recordLlmMetrics(sample, "json", "success", response.usage());
            return Optional.of(response.firstContent());

        } catch (WebClientResponseException e) {
            log.warn("OpenAI Chat API HTTP 에러: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            recordLlmMetrics(sample, "json", "http_error", null);
            return Optional.empty();
        } catch (Exception e) {
            log.warn("OpenAI Chat API 호출 실패: {}", e.getMessage());
            recordLlmMetrics(sample, "json", "error", null);
            return Optional.empty();
        }
    }

    /**
     * OpenAI Structured Outputs (Strict JSON Schema) 방식으로 LLM 응답을 요청합니다.
     * 정의된 JSON 스키마와 100% 일치하는 응답만 반환됨을 보장합니다.
     *
     * @param systemPrompt 시스템 지침
     * @param userPrompt   사용자 요청 또는 컨텍스트
     * @param schemaName   스키마 이름
     * @param jsonSchema   JSON Schema 명세 Map
     * @param maxTokens    최대 토큰 수
     * @return 파싱된 JSON 문자열 (실패 시 Optional.empty())
     */
    public Optional<String> chatStructuredJson(String systemPrompt, String userPrompt, String schemaName, Map<String, Object> jsonSchema, int maxTokens) {
        if (!isConfigured()) {
            log.debug("OpenAI API 키가 설정되지 않아 chatStructuredJson 호출을 건너뜁니다.");
            return Optional.empty();
        }

        Timer.Sample sample = (meterRegistry != null) ? Timer.start(meterRegistry) : null;
        try {
            OpenAiChatRequest request = OpenAiChatRequest.ofStructuredJson(
                    model, systemPrompt, userPrompt, schemaName, jsonSchema, 0.2, maxTokens);

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
                recordLlmMetrics(sample, "structured", "empty", null);
                return Optional.empty();
            }

            if (response.usage() != null) {
                log.info("OpenAI Chat (Structured) 토큰 사용량: prompt={}, completion={}, total={}",
                        response.usage().promptTokens(), response.usage().completionTokens(), response.usage().totalTokens());
            }

            recordLlmMetrics(sample, "structured", "success", response.usage());
            return Optional.of(response.firstContent());

        } catch (WebClientResponseException e) {
            log.warn("OpenAI Chat Structured API HTTP 에러: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            recordLlmMetrics(sample, "structured", "http_error", null);
            return Optional.empty();
        } catch (Exception e) {
            log.warn("OpenAI Chat Structured API 호출 실패: {}", e.getMessage());
            recordLlmMetrics(sample, "structured", "error", null);
            return Optional.empty();
        }
    }

    private void recordLlmMetrics(Timer.Sample sample, String mode, String status, OpenAiChatResponse.Usage usage) {
        if (meterRegistry == null) {
            return;
        }

        if (sample != null) {
            Timer timer = Timer.builder(METRIC_LLM_DURATION)
                    .description("LLM API 호출 소요 시간")
                    .tag("model", model)
                    .tag("mode", mode)
                    .tag("status", status)
                    .register(meterRegistry);
            sample.stop(timer);
        }

        Counter.builder(METRIC_LLM_CALL)
                .description("LLM API 호출 건수")
                .tag("model", model)
                .tag("mode", mode)
                .tag("status", status)
                .register(meterRegistry)
                .increment();

        if (usage != null) {
            Counter.builder(METRIC_LLM_TOKENS)
                    .description("LLM 프롬프트 토큰 사용량")
                    .tag("model", model)
                    .tag("mode", mode)
                    .tag("type", "prompt")
                    .register(meterRegistry)
                    .increment(usage.promptTokens());

            Counter.builder(METRIC_LLM_TOKENS)
                    .description("LLM 완성 토큰 사용량")
                    .tag("model", model)
                    .tag("mode", mode)
                    .tag("type", "completion")
                    .register(meterRegistry)
                    .increment(usage.completionTokens());

            Counter.builder(METRIC_LLM_TOKENS)
                    .description("LLM 총합 토큰 사용량")
                    .tag("model", model)
                    .tag("mode", mode)
                    .tag("type", "total")
                    .register(meterRegistry)
                    .increment(usage.totalTokens());
        }
    }
}
