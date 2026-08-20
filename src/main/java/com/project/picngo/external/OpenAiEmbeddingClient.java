package com.project.picngo.external;

import com.project.picngo.external.dto.OpenAiEmbeddingResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * OpenAI 임베딩 API 클라이언트. search-eval/lib/embedding.js에서 검증한 것과
 * 같은 API를 부른다 - 그쪽에서 이미 "큰 모델도 이름·주소만으로는 동의어·자연어
 * 검색을 못 푼다"까지 실측했으므로, 이 클라이언트가 하는 일은 그 실험을
 * 운영 서비스에 그대로 옮기는 것뿐이다.
 *
 * <p>서킷브레이커를 안 붙인 이유: 이 클라이언트를 부르는 자리(4층 검색 폴백)는
 * 앞의 세 단계가 전부 0건일 때만 도는 마지막 수단이라 호출 빈도가 낮다.
 * 실패해도 검색 결과가 0건으로 남을 뿐 장애로 번지지 않는다. 만약 실사용
 * 트래픽에서 이 호출이 잦아지고 실패율이 문제가 되면, KakaoLocalSearchClient처럼
 * CircuitBreaker를 추가하는 게 다음 단계다.
 */
@Slf4j
@Component
public class OpenAiEmbeddingClient implements EmbeddingClient {

    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(5);

    private final WebClient webClient;
    private final String apiKey;
    private final String model;

    public OpenAiEmbeddingClient(
            WebClient.Builder webClientBuilder,
            @Value("${picngo.embedding.api-key:}") String apiKey,
            @Value("${picngo.embedding.model:text-embedding-3-small}") String model,
            @Value("${picngo.embedding.base-url:https://api.openai.com/v1/embeddings}") String baseUrl) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public Optional<float[]> embed(String text) {
        if (apiKey == null || apiKey.isBlank()) {
            // 키가 없는 채로 배포되는 걸 조용히 넘기지 않는다. 다만 요청마다 로그가
            // 쌓이면 시끄러우니 debug로 낮춰둔다 - 4층을 안 쓰는 환경(로컬 개발 등)에서는
            // 흔한 정상 상태다.
            log.debug("picngo.embedding.api-key가 비어 있어 임베딩 호출을 건너뛴다.");
            return Optional.empty();
        }
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }

        try {
            OpenAiEmbeddingResponse response = webClient.post()
                    .header("Authorization", "Bearer " + apiKey)
                    .bodyValue(Map.of("model", model, "input", text))
                    .retrieve()
                    .bodyToMono(OpenAiEmbeddingResponse.class)
                    .timeout(CALL_TIMEOUT)
                    .block();

            if (response == null || response.data() == null || response.data().isEmpty()) {
                log.warn("❌ [임베딩 응답 형식 오류] 응답: {}", response);
                return Optional.empty();
            }

            List<Float> embedding = response.data().get(0).embedding();
            float[] vector = new float[embedding.size()];
            for (int i = 0; i < vector.length; i++) {
                vector[i] = embedding.get(i);
            }
            return Optional.of(vector);
        } catch (WebClientResponseException e) {
            log.warn("❌ 임베딩 API 오류: {} - 응답 본문: {}", e.getStatusCode(), e.getResponseBodyAsString());
            return Optional.empty();
        } catch (Exception e) {
            log.warn("❌ 임베딩 호출 실패: {}", e.toString());
            return Optional.empty();
        }
    }
}
