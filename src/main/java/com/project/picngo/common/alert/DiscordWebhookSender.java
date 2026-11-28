package com.project.picngo.common.alert;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.Map;

/**
 * 디스코드 웹훅으로 메시지를 보낸다. 실패해도 예외를 밖으로 던지지 않는다.
 */
@Slf4j
@Component
public class DiscordWebhookSender {

    // 알림 때문에 스레드가 오래 묶이지 않도록 짧게 끊는다.
    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final RestClient restClient;

    public DiscordWebhookSender(RestClient.Builder restClientBuilder) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(TIMEOUT);
        requestFactory.setReadTimeout(TIMEOUT);
        this.restClient = restClientBuilder.requestFactory(requestFactory).build();
    }

    public void send(String webhookUrl, Map<String, Object> payload) {
        try {
            restClient.post()
                    .uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            log.warn("디스코드 에러 알림 전송 실패 (status: {})", e.getStatusCode().value());
        } catch (Exception e) {
            // 연결 오류 메시지에는 요청 URL(=웹훅 비밀값)이 들어갈 수 있어 예외 종류만 남긴다.
            log.warn("디스코드 에러 알림 전송 실패 ({})", e.getClass().getSimpleName());
        }
    }
}
