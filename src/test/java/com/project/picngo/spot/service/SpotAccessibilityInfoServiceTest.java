package com.project.picngo.spot.service;

import com.project.picngo.common.exception.CustomException;
import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.domain.SpotAccessibilityInfo;
import com.project.picngo.spot.repository.SpotAccessibilityInfoRepository;
import com.project.picngo.spot.repository.SpotRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class SpotAccessibilityInfoServiceTest {
    @Mock SpotRepository spotRepository;
    @Mock SpotAccessibilityInfoRepository repository;
    @InjectMocks SpotAccessibilityInfoService service;

    @Test void returnsEmptyWhenSpotHasNoAccessibilityInfo() {
        given(spotRepository.existsById(1L)).willReturn(true);
        given(repository.findBySpotId(1L)).willReturn(Optional.empty());
        assertThat(service.getAccessibilityInfo(1L)).isEmpty();
    }

    @Test void throwsWhenSpotDoesNotExist() {
        given(spotRepository.existsById(1L)).willReturn(false);
        assertThatThrownBy(() -> service.getAccessibilityInfo(1L)).isInstanceOf(CustomException.class);
    }

    @Test void returnsStoredAccessibilityInfo() {
        Spot spot = org.mockito.Mockito.mock(Spot.class);
        given(spot.getId()).willReturn(1L);
        given(spotRepository.existsById(1L)).willReturn(true);
        given(repository.findBySpotId(1L)).willReturn(Optional.of(new SpotAccessibilityInfo(spot)));
        assertThat(service.getAccessibilityInfo(1L)).get().extracting("spotId").isEqualTo(1L);
    }
}
