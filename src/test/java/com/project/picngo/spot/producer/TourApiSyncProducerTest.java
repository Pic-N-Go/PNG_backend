package com.project.picngo.spot.producer;

import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.SpotErrorCode;
import com.project.picngo.spot.config.TourApiRabbitMQConfig;
import com.project.picngo.spot.dto.TourApiSyncMessage;
import com.project.picngo.spot.service.TourApiSyncStatusManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TourApiSyncProducerTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private TourApiSyncStatusManager syncStatusManager;

    @InjectMocks
    private TourApiSyncProducer tourApiSyncProducer;

    @Test
    @DisplayName("지역 동기화 요청 시 락 획득 후 RabbitMQ 큐에 정상 발행된다")
    void sendAreaSyncSuccess() {
        given(syncStatusManager.tryLock(anyString(), eq(34))).willReturn(true);

        tourApiSyncProducer.sendAreaSync(34, 1, 5, 100L);

        ArgumentCaptor<TourApiSyncMessage> captor = ArgumentCaptor.forClass(TourApiSyncMessage.class);
        verify(rabbitTemplate).convertAndSend(
                eq(TourApiRabbitMQConfig.EXCHANGE_NAME),
                eq(TourApiRabbitMQConfig.ROUTING_KEY),
                captor.capture()
        );

        TourApiSyncMessage message = captor.getValue();
        assertThat(message.syncType()).isEqualTo(TourApiSyncMessage.SyncType.AREA);
        assertThat(message.areaCode()).isEqualTo(34);
        assertThat(message.startPage()).isEqualTo(1);
        assertThat(message.endPage()).isEqualTo(5);
        assertThat(message.adminId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("이미 동기화 작업이 실행 중인 경우 409 CONFLICT 예외가 발생한다")
    void sendAreaSyncThrowsWhenAlreadyRunning() {
        given(syncStatusManager.tryLock(anyString(), anyInt())).willReturn(false);

        assertThatThrownBy(() -> tourApiSyncProducer.sendAreaSync(34, null, null, 100L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(SpotErrorCode.SYNC_ALREADY_IN_PROGRESS);
    }
}
