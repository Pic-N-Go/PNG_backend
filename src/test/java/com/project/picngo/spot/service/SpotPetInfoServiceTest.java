package com.project.picngo.spot.service;

import com.project.picngo.common.exception.CustomException;
import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.domain.SpotPetInfo;
import com.project.picngo.spot.dto.SpotPetInfoResponse;
import com.project.picngo.spot.repository.SpotPetInfoRepository;
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
class SpotPetInfoServiceTest {

    @Mock
    private SpotRepository spotRepository;

    @Mock
    private SpotPetInfoRepository spotPetInfoRepository;

    @InjectMocks
    private SpotPetInfoService spotPetInfoService;

    @Test
    void returnsEmptyWhenSpotExistsWithoutPetInfo() {
        given(spotRepository.existsById(1L)).willReturn(true);
        given(spotPetInfoRepository.findBySpotId(1L)).willReturn(Optional.empty());

        assertThat(spotPetInfoService.getPetInfo(1L)).isEmpty();
    }

    @Test
    void throwsWhenSpotDoesNotExist() {
        given(spotRepository.existsById(404L)).willReturn(false);

        assertThatThrownBy(() -> spotPetInfoService.getPetInfo(404L))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void returnsStoredPetInfo() {
        Spot spot = org.mockito.Mockito.mock(Spot.class);
        given(spot.getId()).willReturn(1L);
        SpotPetInfo info = new SpotPetInfo(spot);

        given(spotRepository.existsById(1L)).willReturn(true);
        given(spotPetInfoRepository.findBySpotId(1L)).willReturn(Optional.of(info));

        Optional<SpotPetInfoResponse> result = spotPetInfoService.getPetInfo(1L);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().spotId()).isEqualTo(1L);
    }
}
