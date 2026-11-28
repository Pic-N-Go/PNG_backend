package com.project.picngo.common.alert;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ServerErrorAlerterTest {

    private static final String WEBHOOK = "https://discord.example/api/webhooks/test";

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private DiscordWebhookSender sender;

    private ServerErrorAlerter alerter;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        // 테스트에서는 전송을 같은 스레드에서 바로 실행해 결과를 확인한다.
        alerter = new ServerErrorAlerter(WEBHOOK, "prod", redisTemplate, sender, Runnable::run);
    }

    @Test
    @DisplayName("웹훅 URL이 없으면 Redis도 조회하지 않고 아무것도 보내지 않는다")
    void disabledWithoutWebhook() {
        ServerErrorAlerter disabled = new ServerErrorAlerter("  ", "local", redisTemplate, sender, Runnable::run);

        disabled.alert(new IllegalStateException("boom"), "GET", "/spots");

        assertThat(disabled.isEnabled()).isFalse();
        verifyNoInteractions(redisTemplate, sender);
    }

    @Test
    @DisplayName("처음 보는 에러는 10분 중복 방지 키를 잡고 디스코드로 보낸다")
    void sendsFirstOccurrence() {
        given(valueOperations.setIfAbsent(startsWith(ServerErrorAlerter.DEDUPE_KEY_PREFIX), eq("1"), eq(Duration.ofMinutes(10))))
                .willReturn(true);

        alerter.alert(new IllegalStateException("boom"), "PATCH", "/courses/12");

        verify(sender).send(eq(WEBHOOK), anyMap());
    }

    @Test
    @DisplayName("10분 안에 같은 지문의 에러가 다시 나면 보내지 않는다")
    void skipsDuplicateInWindow() {
        given(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).willReturn(false);

        alerter.alert(new IllegalStateException("boom"), "GET", "/spots");

        verify(sender, never()).send(anyString(), anyMap());
    }

    @Test
    @DisplayName("Redis가 실패하면 보내지 않고, 예외도 밖으로 던지지 않는다")
    void redisFailureIsSilent() {
        given(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .willThrow(new RedisConnectionFailureException("down"));

        assertThatCode(() -> alerter.alert(new IllegalStateException("boom"), "GET", "/spots"))
                .doesNotThrowAnyException();
        verify(sender, never()).send(anyString(), anyMap());
    }

    @Test
    @DisplayName("메시지에는 위치·요청·지문이 들어가고, 웹훅 URL은 들어가지 않는다")
    @SuppressWarnings("unchecked")
    void payloadContents() {
        given(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).willReturn(true);
        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);

        alerter.alert(new IllegalStateException("boom"), "PATCH", "/courses/12");

        verify(sender).send(eq(WEBHOOK), payload.capture());
        Map<String, Object> embed = ((List<Map<String, Object>>) payload.getValue().get("embeds")).get(0);
        String description = (String) embed.get("description");
        assertThat((String) embed.get("title")).contains("IllegalStateException");
        assertThat(description).contains("ServerErrorAlerterTest.").contains("PATCH /courses/12").contains("boom");
        assertThat(payload.getValue().toString()).doesNotContain(WEBHOOK);
    }

    @Test
    @DisplayName("긴 메시지는 한 줄로 줄이고 300자에서 자른다")
    void summarizesMessage() {
        String longMessage = "a\nb\t" + "x".repeat(400);

        String summary = ServerErrorAlerter.summarize(longMessage);

        assertThat(summary).doesNotContain("\n").startsWith("a b x").endsWith("…");
        assertThat(summary).hasSize(ServerErrorAlerter.MAX_MESSAGE_LENGTH + 1);
        assertThat(ServerErrorAlerter.summarize(null)).isEqualTo("(메시지 없음)");
    }
}
