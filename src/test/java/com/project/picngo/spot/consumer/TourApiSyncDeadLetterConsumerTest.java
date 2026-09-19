package com.project.picngo.spot.consumer;

import com.project.picngo.spot.dto.AccessibilityTourSyncMessage;
import com.project.picngo.spot.dto.PetTourSyncMessage;
import com.project.picngo.spot.dto.TourApiSyncMessage;
import com.project.picngo.spot.service.TourApiSyncStatusManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TourApiSyncDeadLetterConsumerTest {

    @Mock
    private TourApiSyncStatusManager statusManager;

    @InjectMocks
    private TourApiSyncDeadLetterConsumer consumer;

    @Test
    void marksPetStageFailedWhenRetriesAreExhausted() {
        PetTourSyncMessage message = new PetTourSyncMessage(
                "job-1", List.of(12), 1_000, null, 1L,
                TourApiSyncMessage.SyncType.ALL, LocalDateTime.now());

        consumer.consumePetFailure(message);

        verify(statusManager).markStageFailed(
                org.mockito.ArgumentMatchers.eq("job-1"),
                org.mockito.ArgumentMatchers.eq(TourApiSyncStatusManager.Stage.PET),
                contains("최종 실패"));
    }

    @Test
    void marksAccessibilityStageFailedWhenRetriesAreExhausted() {
        AccessibilityTourSyncMessage message = new AccessibilityTourSyncMessage(
                "job-1", List.of(12), 1_000, null, 1L,
                TourApiSyncMessage.SyncType.ALL, LocalDateTime.now());

        consumer.consumeAccessibilityFailure(message);

        verify(statusManager).markStageFailed(
                org.mockito.ArgumentMatchers.eq("job-1"),
                org.mockito.ArgumentMatchers.eq(TourApiSyncStatusManager.Stage.ACCESSIBILITY),
                contains("최종 실패"));
    }
}
