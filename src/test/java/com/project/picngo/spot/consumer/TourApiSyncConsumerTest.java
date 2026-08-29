package com.project.picngo.spot.consumer;

import com.project.picngo.admin.audit.domain.AdminActionType;
import com.project.picngo.admin.audit.service.AdminAuditLogService;
import com.project.picngo.spot.dto.TourApiSyncMessage;
import com.project.picngo.spot.service.TourApiSyncService;
import com.project.picngo.spot.service.TourApiSyncStatusManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TourApiSyncConsumerTest {

    @Mock
    private TourApiSyncService tourApiSyncService;

    @Mock
    private TourApiSyncStatusManager syncStatusManager;

    @Mock
    private AdminAuditLogService adminAuditLogService;

    @InjectMocks
    private TourApiSyncConsumer tourApiSyncConsumer;

    @Test
    @DisplayName("지역 동기화 큐 메시지 수신 시 syncService 호출, 감사로그 기록 및 성공 처리 검증")
    void consumeAreaSyncSuccess() {
        TourApiSyncMessage message = TourApiSyncMessage.ofArea(34, 1, 5, 100L);
        given(tourApiSyncService.sync(34, 1, 5)).willReturn(45);

        tourApiSyncConsumer.consume(message);

        verify(tourApiSyncService).sync(34, 1, 5);
        verify(adminAuditLogService).record(eq(100L), eq(AdminActionType.TOUR_API_SYNC), anyString(), eq("AREA_34"), anyString(), isNull());
        verify(syncStatusManager).markSuccess(45);
        verify(syncStatusManager).releaseLock();
    }

    @Test
    @DisplayName("동기화 중 예외 발생 시 markFailed 호출 및 락 해제 검증")
    void consumeFailureHandlesError() {
        TourApiSyncMessage message = TourApiSyncMessage.ofAll(100L);
        given(tourApiSyncService.syncAll()).willThrow(new RuntimeException("API 서버 오류"));

        tourApiSyncConsumer.consume(message);

        verify(syncStatusManager).markFailed("API 서버 오류");
        verify(syncStatusManager).releaseLock();
    }
}
