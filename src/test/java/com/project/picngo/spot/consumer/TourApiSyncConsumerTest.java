package com.project.picngo.spot.consumer;

import com.project.picngo.admin.audit.domain.AdminActionType;
import com.project.picngo.admin.audit.service.AdminAuditLogService;
import com.project.picngo.spot.dto.TourApiSyncMessage;
import com.project.picngo.spot.producer.PetTourSyncProducer;
import com.project.picngo.spot.producer.AccessibilityTourSyncProducer;
import com.project.picngo.spot.service.TourApiSyncService;
import com.project.picngo.spot.service.TourApiSyncStatusManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TourApiSyncConsumerTest {

    @Mock
    private TourApiSyncService tourApiSyncService;

    @Mock
    private TourApiSyncStatusManager syncStatusManager;

    @Mock
    private AdminAuditLogService adminAuditLogService;

    @Mock
    private PetTourSyncProducer petTourSyncProducer;

    @Mock
    private AccessibilityTourSyncProducer accessibilityTourSyncProducer;

    @InjectMocks
    private TourApiSyncConsumer tourApiSyncConsumer;

    @Test
    @DisplayName("지역 Spot 동기화 완료 후 후속 메시지를 발행하고 파이프라인 상태를 갱신한다")
    void consumeAreaSyncSuccess() {
        TourApiSyncMessage message = TourApiSyncMessage.ofArea(34, 1, 5, 100L);
        given(tourApiSyncService.sync(34, 1, 5)).willReturn(45);

        tourApiSyncConsumer.consume(message);

        verify(tourApiSyncService).sync(34, 1, 5);
        verify(adminAuditLogService).record(eq(100L), eq(AdminActionType.TOUR_API_SYNC), anyString(), eq("AREA_34"), anyString(), isNull());
        verify(syncStatusManager).markSpotCompleted(message.jobId(), 45);
        verify(petTourSyncProducer).sendAfterSpotSync(message);
        verify(accessibilityTourSyncProducer).sendAfterSpotSync(message);
    }

    @Test
    @DisplayName("동기화 중 예외 발생 시 현재 작업을 재시도 상태로 변경한다")
    void consumeFailureHandlesError() {
        TourApiSyncMessage message = TourApiSyncMessage.ofAll("job-1", 100L);
        given(tourApiSyncService.syncAll()).willThrow(new RuntimeException("API 서버 오류"));

        assertThatThrownBy(() -> tourApiSyncConsumer.consume(message))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("API 서버 오류");

        verify(syncStatusManager).markStageRetrying(
                "job-1", TourApiSyncStatusManager.Stage.SPOT, "API 서버 오류");
        verify(adminAuditLogService).record(eq(100L), eq(AdminActionType.TOUR_API_SYNC), anyString(), eq("ALL_AREAS"), contains("API 서버 오류"), isNull());
    }

    @Test
    @DisplayName("후속 메시지 발행 실패 시 예외를 전파하여 RabbitMQ 재시도를 요청한다")
    void propagateAddonMessagePublicationFailure() {
        TourApiSyncMessage message = TourApiSyncMessage.ofSample("job-1", 3, 100L);
        given(tourApiSyncService.syncSample(3)).willReturn(0);
        willThrow(new RuntimeException("RabbitMQ 발행 오류"))
                .given(petTourSyncProducer)
                .sendAfterSpotSync(message);

        assertThatThrownBy(() -> tourApiSyncConsumer.consume(message))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("RabbitMQ 발행 오류");

        verify(syncStatusManager).markStageRetrying(
                "job-1", TourApiSyncStatusManager.Stage.SPOT, "RabbitMQ 발행 오류");
    }
}
