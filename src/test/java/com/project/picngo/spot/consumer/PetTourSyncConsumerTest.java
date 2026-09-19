package com.project.picngo.spot.consumer;

import com.project.picngo.spot.dto.PetTourSyncMessage;
import com.project.picngo.spot.dto.PetTourSyncResultResponse;
import com.project.picngo.spot.dto.TourApiSyncMessage;
import com.project.picngo.spot.service.PetTourSyncService;
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
class PetTourSyncConsumerTest {

    @Mock
    private PetTourSyncService petTourSyncService;

    @InjectMocks
    private PetTourSyncConsumer consumer;

    @Test
    void syncsEachContentTypeSequentially() {
        PetTourSyncMessage message = message(List.of(12, 14));
        given(petTourSyncService.syncMatchedSpots(12, 1_000, null))
                .willReturn(result(12));
        given(petTourSyncService.syncMatchedSpots(14, 1_000, null))
                .willReturn(result(14));

        consumer.consume(message);

        verify(petTourSyncService).syncMatchedSpots(12, 1_000, null);
        verify(petTourSyncService).syncMatchedSpots(14, 1_000, null);
    }

    @Test
    void rethrowsFailureSoRabbitRetryAndDeadLetterCanHandleIt() {
        PetTourSyncMessage message = message(List.of(12));
        given(petTourSyncService.syncMatchedSpots(12, 1_000, null))
                .willThrow(new IllegalStateException("외부 API 오류"));

        assertThatThrownBy(() -> consumer.consume(message))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("외부 API 오류");
    }

    private PetTourSyncMessage message(List<Integer> types) {
        return new PetTourSyncMessage(types, 1_000, null, 1L,
                TourApiSyncMessage.SyncType.SAMPLE, LocalDateTime.now());
    }

    private PetTourSyncResultResponse result(int contentTypeId) {
        return new PetTourSyncResultResponse(contentTypeId, 0, 0, 0, 0, 0);
    }
}
