package com.project.picngo.spot.service;

import com.project.picngo.external.KakaoLocalSearchClient;
import com.project.picngo.external.dto.PlaceSearchResult;
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
                .build();
        spot.getAccessPoints().add(accessPoint);
        spot.assignPrimaryAccessPoint(accessPoint);

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
                .thenReturn(PlaceSearchResult.found(mockParking));

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
                .thenReturn(PlaceSearchResult.found(farParking));

        Coordinate result = spotNavigationService.correctSpotNavigation(spot);

        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("외딴 해변 스팟");
        assertThat(result.latitude()).isEqualTo(33.5000);
        assertThat(spot.getAccessType()).isEqualTo(AccessType.RESOLVE_FAILED);

        verify(spotAccessPointRepository, never()).save(any());
        verify(spotRepository, times(1)).save(spot);
    }

    @Test
    @DisplayName("주차장이 2km를 넘으면 포기하지 않고 매표소 검색으로 넘어간다")
    void testCorrectSpotNavigation_FallsThroughToTicketOfficeWhenParkingTooFar() {
        Spot spot = Spot.builder()
                .name("한라산 백록담")
                .address("제주 서귀포시 토평동")
                .latitude(33.3617)
                .longitude(126.5382)
                .build();

        // "주차장"은 반대편 탐방로가 걸려 10km 밖 -> 게이트 탈락
        when(kakaoLocalSearchClient.searchNearbyPlace(eq("한라산 백록담 주차장"), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(PlaceSearchResult.found(new Coordinate(33.4500, 126.6000, "반대편 탐방로 주차장")));
        // "매표소"는 500m 이내 -> 게이트 통과
        when(kakaoLocalSearchClient.searchNearbyPlace(eq("한라산 백록담 매표소"), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(PlaceSearchResult.found(new Coordinate(33.3650, 126.5400, "성판악 매표소")));

        Coordinate result = spotNavigationService.correctSpotNavigation(spot);

        assertThat(result.name()).isEqualTo("성판악 매표소");
        assertThat(spot.getAccessType()).isEqualTo(AccessType.NEEDS_ENTRANCE);

        verify(spotAccessPointRepository, times(1)).save(any(SpotAccessPoint.class));
    }

    @Test
    @DisplayName("검색 API 호출이 실패하면 RESOLVE_FAILED로 확정하지 않고 상태를 유지한다")
    void testCorrectSpotNavigation_SearchApiError_KeepsStateForRetry() {
        Spot spot = Spot.builder()
                .name("성산일출봉")
                .address("제주 서귀포시 성산읍")
                .latitude(33.458)
                .longitude(126.9425)
                .build();

        // 카카오 권한 오류(403)나 네트워크 장애 상황
        when(kakaoLocalSearchClient.searchNearbyPlace(anyString(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(PlaceSearchResult.error());

        Coordinate result = spotNavigationService.correctSpotNavigation(spot);

        // 원본 좌표로 폴백하되, 상태는 건드리지 않아 다음 요청에서 다시 시도할 수 있어야 한다
        assertThat(result.latitude()).isEqualTo(33.458);
        assertThat(spot.getAccessType()).isEqualTo(AccessType.UNKNOWN);

        verify(spotAccessPointRepository, never()).save(any());
        verify(spotRepository, never()).save(any());

        // 첫 호출이 실패하면 남은 검색어는 태우지 않는다
        verify(kakaoLocalSearchClient, times(1))
                .searchNearbyPlace(anyString(), anyDouble(), anyDouble(), anyInt());
    }

    @Test
    @DisplayName("주차장/매표소 검색 결과가 모두 0건이면 RESOLVE_FAILED로 확정한다")
    void testCorrectSpotNavigation_NoResults_MarksResolveFailed() {
        Spot spot = Spot.builder()
                .name("이름없는 오지 스팟")
                .address("제주 제주시")
                .latitude(33.5000)
                .longitude(126.5000)
                .build();

        when(kakaoLocalSearchClient.searchNearbyPlace(anyString(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(PlaceSearchResult.notFound());

        Coordinate result = spotNavigationService.correctSpotNavigation(spot);

        assertThat(result.name()).isEqualTo("이름없는 오지 스팟");
        assertThat(spot.getAccessType()).isEqualTo(AccessType.RESOLVE_FAILED);

        // 주차장 -> 매표소 두 검색어를 모두 시도한 뒤에 실패로 확정한다
        verify(kakaoLocalSearchClient, times(2))
                .searchNearbyPlace(anyString(), anyDouble(), anyDouble(), anyInt());
        verify(spotRepository, times(1)).save(spot);
    }
}
