package com.project.picngo.notification.service;

import com.project.picngo.notification.domain.NotificationSetting;
import com.project.picngo.notification.producer.NotificationPushProducer;
import com.project.picngo.notification.repository.NotificationRepository;
import com.project.picngo.notification.repository.NotificationSettingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private NotificationSettingRepository notificationSettingRepository;
    @Mock
    private FcmService fcmService;
    @Mock
    private NotificationCacheService notificationCacheService;
    @Mock
    private NotificationPushProducer notificationPushProducer;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    @DisplayName("실패한 토큰이 저장된 토큰과 일치하면 토큰을 정리하고 캐시를 동기화한다")
    void handleInvalidToken_matching_clears() {
        NotificationSetting setting = NotificationSetting.builder().userId(1L).fcmToken("dead-token").build();
        when(notificationSettingRepository.findByUserId(1L)).thenReturn(Optional.of(setting));

        notificationService.handleInvalidToken(1L, "dead-token");

        assertThat(setting.getFcmToken()).isNull();
        verify(notificationCacheService).updateCachedSetting(1L, setting);
    }

    @Test
    @DisplayName("그 사이 토큰이 갱신되어 실패 토큰과 다르면 정리하지 않는다(정상 토큰 보존)")
    void handleInvalidToken_mismatch_skips() {
        NotificationSetting setting = NotificationSetting.builder().userId(1L).fcmToken("new-token").build();
        when(notificationSettingRepository.findByUserId(1L)).thenReturn(Optional.of(setting));

        notificationService.handleInvalidToken(1L, "dead-token");

        assertThat(setting.getFcmToken()).isEqualTo("new-token"); // 보존됨
        verify(notificationCacheService, never()).updateCachedSetting(anyLong(), any());
    }
}
