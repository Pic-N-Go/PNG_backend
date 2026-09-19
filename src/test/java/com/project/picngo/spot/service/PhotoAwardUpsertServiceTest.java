package com.project.picngo.spot.service;

import com.project.picngo.common.domain.SpotCategory;
import com.project.picngo.common.event.SpotCreatedEvent;
import com.project.picngo.external.KakaoAddressClient;
import com.project.picngo.external.dto.PhotoAwardApiResponse.PhotoAwardItem;
import com.project.picngo.spot.domain.PhotoAward;
import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.domain.enums.SpotSource;
import com.project.picngo.spot.domain.enums.SpotStatus;
import com.project.picngo.spot.dto.Coordinate;
import com.project.picngo.spot.repository.PhotoAwardRepository;
import com.project.picngo.spot.repository.SpotPhotoRepository;
import com.project.picngo.spot.repository.SpotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PhotoAwardUpsertServiceTest {

    @Mock
    private SpotRepository spotRepository;

    @Mock
    private SpotPhotoRepository spotPhotoRepository;

    @Mock
    private PhotoAwardRepository photoAwardRepository;

    @Mock
    private KakaoAddressClient kakaoAddressClient;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private PhotoAwardUpsertService upsertService;

    private PhotoAwardItem sampleItem;
    private Coordinate sampleCoord;

    @BeforeEach
    void setUp() {
        sampleItem = new PhotoAwardItem(
                "AWARD123",
                "광안대교의 새벽",
                "Dawn of Gwangan Bridge",
                "26",
                "부산 수영구 남천동, 삼익비치아파트",
                "Samick Beach Apt, Namcheon-dong, Suyeong-gu, Busan",
                "202304",
                "홍길동",
                "Hong Gil-dong",
                "디지털카메라 부문 [금상]",
                "Gold Prize",
                "부산, 광안대교, 벚꽃, 일출",
                "Busan, Cherry Blossom, Sunrise",
                "https://example.com/origin.jpg",
                "https://example.com/thumb.jpg",
                "Type1",
                "20240101",
                "20240102",
                "1"
        );

        sampleCoord = new Coordinate(35.1432, 129.1123, "삼익비치타운아파트");
    }

    @Test
    @DisplayName("이미 등록된 contentId는 건너뛴다 (SKIPPED)")
    void skipAlreadyRegisteredAward() {
        given(photoAwardRepository.existsByContentId("AWARD123")).willReturn(true);

        PhotoAwardUpsertService.UpsertResult result = upsertService.upsertAward(sampleItem, sampleCoord);

        assertThat(result).isEqualTo(PhotoAwardUpsertService.UpsertResult.SKIPPED);
    }

    @Test
    @DisplayName("300m 이내 기존 스팟이 있으면 뱃지를 부여하고 사진을 보강한다 (ENRICHED)")
    void enrichExistingSpotWithin300m() {
        given(photoAwardRepository.existsByContentId("AWARD123")).willReturn(false);

        Spot existingSpot = Spot.builder()
                .name("삼익비치 벚꽃거리")
                .address("부산 수영구 남천동")
                .latitude(35.1430)
                .longitude(129.1120)
                .categories(Set.of(SpotCategory.FLOWER))
                .source(SpotSource.TOUR_API)
                .badge(false)
                .build();
        ReflectionTestUtils.setField(existingSpot, "id", 100L);

        given(spotRepository.findNearbySpots(eq(35.1432), eq(129.1123), eq(0.3), eq(5)))
                .willReturn(List.of(existingSpot));

        PhotoAwardUpsertService.UpsertResult result = upsertService.upsertAward(sampleItem, sampleCoord);

        assertThat(result).isEqualTo(PhotoAwardUpsertService.UpsertResult.ENRICHED);
        assertThat(existingSpot.getBadge()).isTrue();
        assertThat(existingSpot.getImageUrl()).isEqualTo("https://example.com/origin.jpg");
        verify(spotPhotoRepository).save(any());
        verify(photoAwardRepository).save(any(PhotoAward.class));
    }

    @Test
    @DisplayName("300m 이내 스팟이 없으면 PHOTO_CONTEST 출처의 신규 스팟을 자동 생성한다 (CREATED)")
    void createNewSpotWhenNoneNearby() {
        given(photoAwardRepository.existsByContentId("AWARD123")).willReturn(false);
        given(spotRepository.findNearbySpots(eq(35.1432), eq(129.1123), eq(0.3), eq(5)))
                .willReturn(Collections.emptyList());
        given(kakaoAddressClient.coord2Address(35.1432, 129.1123))
                .willReturn("부산광역시 수영구 광안해변로 100");

        Spot savedSpot = Spot.builder()
                .name("삼익비치타운아파트")
                .address("부산광역시 수영구 광안해변로 100")
                .latitude(35.1432)
                .longitude(129.1123)
                .categories(Set.of(SpotCategory.FLOWER))
                .source(SpotSource.PHOTO_CONTEST)
                .badge(true)
                .status(SpotStatus.APPROVED)
                .photogenicScore(0)
                .build();
        ReflectionTestUtils.setField(savedSpot, "id", 200L);

        given(spotRepository.save(any(Spot.class))).willReturn(savedSpot);

        PhotoAwardUpsertService.UpsertResult result = upsertService.upsertAward(sampleItem, sampleCoord);

        assertThat(result).isEqualTo(PhotoAwardUpsertService.UpsertResult.CREATED);
        verify(spotRepository).save(any(Spot.class));
        verify(eventPublisher).publishEvent(any(SpotCreatedEvent.class));
        verify(spotPhotoRepository).save(any());
        verify(photoAwardRepository).save(any(PhotoAward.class));
    }
}
