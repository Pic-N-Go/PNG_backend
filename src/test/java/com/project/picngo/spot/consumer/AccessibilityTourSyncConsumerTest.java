package com.project.picngo.spot.consumer;

import com.project.picngo.spot.dto.AccessibilityTourSyncMessage;
import com.project.picngo.spot.dto.AccessibilityTourSyncResultResponse;
import com.project.picngo.spot.dto.TourApiSyncMessage;
import com.project.picngo.spot.service.AccessibilityTourSyncService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AccessibilityTourSyncConsumerTest {

    @Mock
    private AccessibilityTourSyncService service;

    @InjectMocks
    private AccessibilityTourSyncConsumer consumer;

    @Test
    void syncsEachContentTypeSequentially() {
        AccessibilityTourSyncMessage message = message(List.of(12, 14));
        given(service.syncMatchedSpots(12, 1_000, null)).willReturn(result(12, 0));
        given(service.syncMatchedSpots(14, 1_000, null)).willReturn(result(14, 0));

        consumer.consume(message);

        verify(service).syncMatchedSpots(12, 1_000, null);
        verify(service).syncMatchedSpots(14, 1_000, null);
    }

    @Test
    void processesLaterContentTypesBeforeRequestingRetryForFailedItems() {
        AccessibilityTourSyncMessage message = message(List.of(12, 14));
        given(service.syncMatchedSpots(12, 1_000, null)).willReturn(result(12, 1));
        given(service.syncMatchedSpots(14, 1_000, null)).willReturn(result(14, 0));

        assertThatThrownBy(() -> consumer.consume(message))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("무장애 상세 동기화 실패: 1건");

        verify(service).syncMatchedSpots(14, 1_000, null);
    }

    @Test
    void rethrowsFatalFailureImmediately() {
        AccessibilityTourSyncMessage message = message(List.of(12));
        given(service.syncMatchedSpots(12, 1_000, null))
                .willThrow(new IllegalStateException("목록 API 오류"));

        assertThatThrownBy(() -> consumer.consume(message))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("목록 API 오류");
    }

    private AccessibilityTourSyncMessage message(List<Integer> contentTypeIds) {
        return new AccessibilityTourSyncMessage(
                contentTypeIds,
                1_000,
                null,
                1L,
                TourApiSyncMessage.SyncType.ALL,
                LocalDateTime.now()
        );
    }

    private AccessibilityTourSyncResultResponse result(
            int contentTypeId,
            int failedCount
    ) {
        return new AccessibilityTourSyncResultResponse(
                contentTypeId,
                0,
                0,
                0,
                0,
                failedCount
        );
    }
}
