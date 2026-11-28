package com.project.picngo.common.alert;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * 처리되지 않은 500 에러를 디스코드로 알린다. 어떤 예외가 우리 코드 어디에서 났는지를 바로 보여줘서,
 * 서버 로그를 뒤지지 않고 원인 위치를 알 수 있게 하는 것이 목적이다.
 *
 * <ul>
 *   <li>같은 지문의 에러는 10분 동안 한 번만 보낸다(Redis). 장애 중 같은 에러가 수백 번 나도 채널이 덮이지 않게 한다.</li>
 *   <li>전송은 별도 스레드에서 한다. 알림이 늦거나 실패해도 사용자 응답은 바뀌지 않는다.</li>
 *   <li>웹훅 URL이 없으면(로컬·테스트) 아무것도 하지 않는다. 운영 프로필에서만 값이 들어간다.</li>
 * </ul>
 */
@Slf4j
@Component
public class ServerErrorAlerter {

    static final Duration DEDUPE_WINDOW = Duration.ofMinutes(10);
    static final String DEDUPE_KEY_PREFIX = "alert:server-error:";
    static final int MAX_MESSAGE_LENGTH = 300;
    private static final int DISCORD_RED = 0xE5484D;

    private final String webhookUrl;
    private final String environment;
    private final StringRedisTemplate redisTemplate;
    private final DiscordWebhookSender sender;
    private final Executor executor;

    public ServerErrorAlerter(
            @Value("${alert.discord.webhook-url:}") String webhookUrl,
            @Value("${spring.profiles.active:local}") String environment,
            StringRedisTemplate redisTemplate,
            DiscordWebhookSender sender,
            @Qualifier("errorAlertExecutor") Executor executor
    ) {
        this.webhookUrl = webhookUrl == null ? "" : webhookUrl.trim();
        this.environment = environment;
        this.redisTemplate = redisTemplate;
        this.sender = sender;
        this.executor = executor;
    }

    public boolean isEnabled() {
        return !webhookUrl.isEmpty();
    }

    public void alert(Throwable e, String httpMethod, String path) {
        if (!isEnabled()) {
            return;
        }
        try {
            ErrorFingerprint fingerprint = ErrorFingerprint.of(e);
            if (!isFirstInWindow(fingerprint)) {
                return;
            }
            Map<String, Object> payload = buildPayload(e, fingerprint, httpMethod, path, Instant.now());
            executor.execute(() -> sender.send(webhookUrl, payload));
        } catch (Exception alertFailure) {
            // 알림은 부가 기능이다. 여기서 실패해도 원래 500 응답 흐름을 건드리지 않는다.
            log.warn("500 에러 알림 처리 실패 ({})", alertFailure.getClass().getSimpleName());
        }
    }

    private boolean isFirstInWindow(ErrorFingerprint fingerprint) {
        try {
            Boolean first = redisTemplate.opsForValue()
                    .setIfAbsent(DEDUPE_KEY_PREFIX + fingerprint.hash(), "1", DEDUPE_WINDOW);
            return Boolean.TRUE.equals(first);
        } catch (Exception redisFailure) {
            // Redis가 죽었을 때 묶지 못한 채 보내면 에러마다 알림이 쏟아진다. 이 경우는 보내지 않고,
            // 대신 Grafana의 "500 에러 급증" 알림이 장애를 알려준다.
            log.warn("500 에러 알림 중복 확인 실패 - 전송 생략 ({})", redisFailure.getClass().getSimpleName());
            return false;
        }
    }

    Map<String, Object> buildPayload(Throwable e, ErrorFingerprint fingerprint, String httpMethod, String path, Instant occurredAt) {
        StringBuilder description = new StringBuilder()
                .append("**위치** `").append(fingerprint.location()).append("`\n")
                .append("**요청** `").append(httpMethod).append(' ').append(path).append("`\n")
                .append("**메시지** ").append(summarize(rootMessage(e)));
        if (!fingerprint.appFrames().isEmpty()) {
            description.append("\n```\n").append(String.join("\n", fingerprint.appFrames())).append("\n```");
        }

        List<Map<String, Object>> fields = new ArrayList<>();
        if (!fingerprint.rootCauseType().equals(fingerprint.exceptionType())) {
            fields.add(field("근본 원인", fingerprint.rootCauseType()));
        }
        fields.add(field("지문", "`" + fingerprint.hash() + "`"));
        fields.add(field("환경", environment));

        Map<String, Object> embed = new LinkedHashMap<>();
        embed.put("title", "🔥 처리되지 않은 500 에러 · " + fingerprint.exceptionType());
        embed.put("description", description.toString());
        embed.put("color", DISCORD_RED);
        embed.put("fields", fields);
        embed.put("footer", Map.of("text", "같은 에러는 " + DEDUPE_WINDOW.toMinutes() + "분 동안 다시 보내지 않습니다"));
        // 디스코드가 보는 사람의 시간대로 표시한다.
        embed.put("timestamp", occurredAt.toString());
        return Map.of("embeds", List.of(embed));
    }

    private static Map<String, Object> field(String name, String value) {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("name", name);
        field.put("value", value);
        field.put("inline", true);
        return field;
    }

    private static String rootMessage(Throwable e) {
        Throwable root = ErrorFingerprint.rootCause(e);
        return root.getMessage() != null ? root.getMessage() : e.getMessage();
    }

    // 메시지는 원인 파악에 필요하지만 입력값이 섞일 수 있어, 한 줄로 줄이고 길이를 제한한다.
    static String summarize(String message) {
        if (message == null || message.isBlank()) {
            return "(메시지 없음)";
        }
        String oneLine = message.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= MAX_MESSAGE_LENGTH ? oneLine : oneLine.substring(0, MAX_MESSAGE_LENGTH) + "…";
    }
}
