package com.project.picngo.notification.consumer;

import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.NotificationErrorCode;
import com.project.picngo.notification.domain.Notification;
import com.project.picngo.notification.domain.NotificationSetting;
import com.project.picngo.notification.dto.NotificationPushDto;
import com.project.picngo.notification.repository.NotificationRepository;
import com.project.picngo.notification.repository.NotificationSettingRepository;
import com.project.picngo.notification.service.FcmService;
import com.project.picngo.notification.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationPushConsumerTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private NotificationSettingRepository notificationSettingRepository;
    @Mock
    private FcmService fcmService;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationPushConsumer consumer;

    private NotificationPushDto dto(String dedupeKey) {
        return new NotificationPushDto(1L, "WEATHER_MATCH", "☁️ 오전 날씨 조건 알림",
                "오늘 오전 경복궁의 날씨가 설정하신 조건과 일치할 예정입니다!", "/wishlist/10", 10L, dedupeKey);
    }

    @Test
    @DisplayName("최초 처리: 저장(선점) 성공 시 FCM을 발송한다")
    void firstProcessSendsFcm() {
        NotificationSetting setting = NotificationSetting.builder().userId(1L).fcmToken("valid-token").build();
        when(notificationSettingRepository.findByUserId(1L)).thenReturn(Optional.of(setting));

        consumer.consumePushEvent(dto("WEATHER_MATCH:1:10:20260728:MORNING"));

        verify(notificationRepository).saveAndFlush(any(Notification.class));
        verify(fcmService).sendMessage(eq("valid-token"), anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("중복 처리: 유니크 제약 위반 시 FCM을 발송하지 않고, 예외 없이 정상 종료한다(재전달 방지)")
    void duplicateSkipsFcmAndReturnsNormally() {
        when(notificationRepository.saveAndFlush(any(Notification.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate dedupeKey"));

        // 예외가 리스너 밖으로 전파되지 않아야 한다(전파 시 NACK → 재전달 루프)
        assertThatCode(() -> consumer.consumePushEvent(dto("WEATHER_MATCH:1:10:20260728:MORNING")))
                .doesNotThrowAnyException();

        // 중복이므로 FCM 재발송 및 설정 조회 자체가 없어야 한다
        verify(fcmService, never()).sendMessage(anyString(), anyString(), anyString(), anyString(), anyLong());
        verify(notificationSettingRepository, never()).findByUserId(anyLong());
    }

    @Test
    @DisplayName("토큰이 없으면 저장(인박스)은 하되 FCM은 발송하지 않는다")
    void noTokenStillSavesButSkipsFcm() {
        NotificationSetting setting = NotificationSetting.builder().userId(1L).fcmToken(null).build();
        when(notificationSettingRepository.findByUserId(1L)).thenReturn(Optional.of(setting));

        consumer.consumePushEvent(dto("WEATHER_MATCH:1:10:20260728:MORNING"));

        verify(notificationRepository).saveAndFlush(any(Notification.class));
        verify(fcmService, never()).sendMessage(anyString(), anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("무효 토큰(FCM_TOKEN_INVALID): 토큰 정리(handleInvalidToken)를 호출한다")
    void invalidTokenTriggersCleanup() {
        NotificationSetting setting = NotificationSetting.builder().userId(1L).fcmToken("dead-token").build();
        when(notificationSettingRepository.findByUserId(1L)).thenReturn(Optional.of(setting));
        doThrow(new CustomException(NotificationErrorCode.FCM_TOKEN_INVALID))
                .when(fcmService).sendMessage(anyString(), anyString(), anyString(), anyString(), anyLong());

        consumer.consumePushEvent(dto("WEATHER_MATCH:1:10:20260728:MORNING"));

        verify(notificationService).handleInvalidToken(1L);
    }
}
