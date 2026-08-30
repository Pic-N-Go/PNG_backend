package com.project.picngo.spot.scheduler;

import com.project.picngo.common.domain.SpotCategory;
import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.repository.SpotPhotoRepository;
import com.project.picngo.spot.repository.SpotRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FestivalCleanupSchedulerTest {

    @Mock
    private SpotRepository spotRepository;

    @Mock
    private SpotPhotoRepository spotPhotoRepository;

    @InjectMocks
    private FestivalCleanupScheduler scheduler;

    @Test
    @DisplayName("종료된 축제가 존재하는 경우 사진 및 스팟 데이터를 영구 삭제(Hard Delete)한다")
    void testCleanupExpiredFestivals() {
        // given
        Spot expiredFestival = mock(Spot.class);
        List<Spot> expiredList = List.of(expiredFestival);

        given(spotRepository.findExpiredFestivals(eq(SpotCategory.FESTIVAL), any(LocalDate.class)))
                .willReturn(expiredList);

        // when
        int deletedCount = scheduler.cleanupExpiredFestivals();

        // then
        assertThat(deletedCount).isEqualTo(1);
        verify(spotPhotoRepository, times(1)).deleteBySpotIn(expiredList);
        verify(spotRepository, times(1)).deleteAll(expiredList);
    }

    @Test
    @DisplayName("종료된 축제가 없는 경우 삭제 작업을 수행하지 않는다")
    void testCleanupNoExpiredFestivals() {
        // given
        given(spotRepository.findExpiredFestivals(eq(SpotCategory.FESTIVAL), any(LocalDate.class)))
                .willReturn(Collections.emptyList());

        // when
        int deletedCount = scheduler.cleanupExpiredFestivals();

        // then
        assertThat(deletedCount).isEqualTo(0);
        verify(spotPhotoRepository, never()).deleteBySpotIn(any());
        verify(spotRepository, never()).deleteAll(any());
    }
}
