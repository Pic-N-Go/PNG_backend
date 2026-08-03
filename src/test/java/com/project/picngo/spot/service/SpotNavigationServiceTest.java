package com.project.picngo.spot.service;

import com.project.picngo.external.KakaoLocalSearchClient;
import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.domain.SpotAccessPoint;
import com.project.picngo.spot.domain.enums.AccessPointSource;
import com.project.picngo.spot.domain.enums.AccessType;
import com.project.picngo.spot.dto.Coordinate;
import com.project.picngo.spot.repository.SpotAccessPointRepository;
import com.project.picngo.spot.repository.SpotRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SpotNavigationServiceTest {

    @Mock
    private KakaoLocalSearchClient kakaoLocalSearchClient;
    @Mock
    private SpotAccessPointRepository spotAccessPointRepository;
    @Mock
    private SpotRepository spotRepository;

    @InjectMocks
    private SpotNavigationService spotNavigationService;

    @Test
    @DisplayName("이미 주차장 보정 좌표가 있는 NEEDS_ENTRANCE 스팟은 기존 보정 좌표 즉시 반환")
    void testCorrectSpotNavigation_AlreadyCorrected() {
        Spot spot = Spot.builder()
                .name("성산일출봉")
                .address("제주 서귀포시 성산읍")
                .latitude(33.458)
                .longitude(126.942)
                .build();
        spot.updateAccessType(AccessType.NEEDS_ENTRANCE);

        SpotAccessPoint accessPoint = SpotAccessPoint.builder()
                .spot(spot)
                .latitude(33.4621)
                .longitude(126.9365)
                .label("성산일출봉 공영주차장")
                .source(AccessPointSource.KAKAO_LOCAL)
                .isPrimary(true)
                .build();
        spot.getAccessPoints().add(accessPoint);

        when(spotAccessPointRepository.findBySpotIdAndIsPrimaryTrue(spot.getId()))
                .thenReturn(java.util.Optional.of(accessPoint));

        Coordinate result = spotNavigationService.correctSpotNavigation(spot);

        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("성산일출봉 공영주차장");
        assertThat(result.latitude()).isEqualTo(33.4621);
        assertThat(result.longitude()).isEqualTo(126.9365);
        verifyNoInteractions(kakaoLocalSearchClient);
    }

    @Test
    @DisplayName("카카오 지도 검색으로 2km 이내 주차장 발견 시 SpotAccessPoint DB 저장 및 NEEDS_ENTRANCE 갱신")
    void testCorrectSpotNavigation_FindNearbyParking() {
        Spot spot = Spot.builder()
                .name("한라산 백록담")
                .address("제주 서귀포시 토평동")
                .latitude(33.3617)
                .longitude(126.5382)
                .build();

        // 500m 떨어진 위치의 가짜 성판악 주차장 좌표
        Coordinate mockParking = new Coordinate(33.3650, 126.5400, "한라산 성판악 주차장");
        when(kakaoLocalSearchClient.searchNearbyPlace(eq("한라산 백록담 주차장"), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(mockParking);

        Coordinate result = spotNavigationService.correctSpotNavigation(spot);

        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("한라산 성판악 주차장");
        assertThat(spot.getAccessType()).isEqualTo(AccessType.NEEDS_ENTRANCE);

        verify(spotAccessPointRepository, times(1)).save(any(SpotAccessPoint.class));
        verify(spotRepository, times(1)).save(spot);
    }

    @Test
    @DisplayName("검색된 주차장이 2km 초과로 너무 멀면 RESOLVE_FAILED 처리 및 원본 좌표 반환")
    void testCorrectSpotNavigation_ParkingTooFar() {
        Spot spot = Spot.builder()
                .name("외딴 해변 스팟")
                .address("제주 제주시")
                .latitude(33.5000)
                .longitude(126.5000)
                .build();

        // 10km 떨어진 엉뚱한 주차장
        Coordinate farParking = new Coordinate(33.6000, 126.6000, "멀리 있는 주차장");
        when(kakaoLocalSearchClient.searchNearbyPlace(anyString(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(farParking);

        Coordinate result = spotNavigationService.correctSpotNavigation(spot);

        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("외딴 해변 스팟");
        assertThat(result.latitude()).isEqualTo(33.5000);
        assertThat(spot.getAccessType()).isEqualTo(AccessType.RESOLVE_FAILED);

        verify(spotAccessPointRepository, never()).save(any());
        verify(spotRepository, times(1)).save(spot);
    }
}
