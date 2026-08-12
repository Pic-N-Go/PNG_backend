package com.project.picngo.spotalert.service;

import com.project.picngo.common.exception.CustomException;
import com.project.picngo.external.service.WeatherCacheService;
import com.project.picngo.notification.repository.NotificationSettingRepository;
import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.repository.SpotRepository;
import com.project.picngo.spot.repository.SpotTagRepository;
import com.project.picngo.spotalert.domain.SpotAlert;
import com.project.picngo.spotalert.dto.SpotAlertActiveUpdateRequest;
import com.project.picngo.spotalert.dto.SpotAlertSettingResponse;
import com.project.picngo.spotalert.repository.SpotAlertRepository;
import com.project.picngo.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class SpotAlertServiceTest {

    @Mock
    private SpotAlertRepository spotAlertRepository;
    @Mock
    private WeatherCacheService weatherCacheService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private SpotRepository spotRepository;
    @Mock
    private SpotTagRepository spotTagRepository;
    @Mock
    private NotificationSettingRepository notificationSettingRepository;
    @Mock
    private WeatherMatchService weatherMatchService;

    @InjectMocks
    private SpotAlertService spotAlertService;

    @Test
    @DisplayName("개별 출사알림 ON/OFF 상태를 성공적으로 변경한다")
    void updateSpotAlertActive_success() {
        // given
        Long userId = 1L;
        Long spotId = 10L;
        SpotAlert spotAlert = SpotAlert.builder()
                .userId(userId)
                .spotId(spotId)
                .isActive(true)
                .build();

        Spot spot = Spot.builder()
                .name("서울숲")
                .address("서울특별시 성동구")
                .latitude(37.5445)
                .longitude(127.0371)
                .build();

        given(spotAlertRepository.findByUserIdAndSpotId(userId, spotId)).willReturn(Optional.of(spotAlert));

        SpotAlertActiveUpdateRequest request = new SpotAlertActiveUpdateRequest(false);

        // when
        com.project.picngo.spotalert.dto.SpotAlertActiveResponse response = spotAlertService.updateSpotAlertActive(userId, spotId, request);

        // then
        assertThat(response).isNotNull();
        assertThat(response.spotId()).isEqualTo(spotId);
        assertThat(response.isAlertEnabled()).isFalse();
        assertThat(spotAlert.getIsActive()).isFalse();
    }

    @Test
    @DisplayName("존재하지 않는 출사알림의 상태 변경 시 예외가 발생한다")
    void updateSpotAlertActive_notFound_throwsException() {
        // given
        Long userId = 1L;
        Long spotId = 999L;

        given(spotAlertRepository.findByUserIdAndSpotId(userId, spotId)).willReturn(Optional.empty());


        SpotAlertActiveUpdateRequest request = new SpotAlertActiveUpdateRequest(true);

        // when & then
        assertThatThrownBy(() -> spotAlertService.updateSpotAlertActive(userId, spotId, request))
                .isInstanceOf(CustomException.class);
    }
}
