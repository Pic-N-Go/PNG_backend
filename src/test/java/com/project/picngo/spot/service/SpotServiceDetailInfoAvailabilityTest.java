package com.project.picngo.spot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.picngo.bookmark.repository.BookmarkCollectionSpotRepository;
import com.project.picngo.external.EmbeddingClient;
import com.project.picngo.external.TarRlteTarApiClient;
import com.project.picngo.spot.config.SearchEngine;
import com.project.picngo.spot.config.SearchProperties;
import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.dto.SpotDetailResponse;
import com.project.picngo.spot.repository.ReviewRepository;
import com.project.picngo.spot.repository.SpotAccessibilityInfoRepository;
import com.project.picngo.spot.repository.SpotPetInfoRepository;
import com.project.picngo.spot.repository.SpotPhotoRepository;
import com.project.picngo.spot.repository.SpotRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SpotServiceDetailInfoAvailabilityTest {

    @Mock SpotRepository spotRepository;
    @Mock ReviewRepository reviewRepository;
    @Mock SpotPhotoRepository spotPhotoRepository;
    @Mock BookmarkCollectionSpotRepository bookmarkCollectionSpotRepository;
    @Mock SpotPetInfoRepository spotPetInfoRepository;
    @Mock SpotAccessibilityInfoRepository spotAccessibilityInfoRepository;
    @Mock EmbeddingClient embeddingClient;
    @Mock TarRlteTarApiClient tarRlteTarApiClient;
    @Mock AdministrativeCodeResolver administrativeCodeResolver;
    @Mock StringRedisTemplate redisTemplate;
    @Spy MeterRegistry meterRegistry = new SimpleMeterRegistry();
    @Spy SearchProperties searchProperties = new SearchProperties(SearchEngine.LIKE, false, false, false, false);
    @Spy ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks SpotService spotService;

    @Test
    void includesPetAndAccessibilityDetailAvailability() {
        long spotId = 1L;
        Spot spot = Spot.builder().name("테스트 스팟").build();
        given(spotRepository.findById(spotId)).willReturn(Optional.of(spot));
        given(reviewRepository.findFrequentTagsBySpotId(spotId)).willReturn(List.of());
        given(reviewRepository.findAvgAndCountBySpotId(spotId)).willReturn(List.of());
        given(spotPhotoRepository.countBySpotId(spotId)).willReturn(0L);
        given(spotPetInfoRepository.existsBySpotId(spotId)).willReturn(true);
        given(spotAccessibilityInfoRepository.existsBySpotId(spotId)).willReturn(false);

        SpotDetailResponse response = spotService.getSpotDetail(spotId, null);

        assertThat(response.hasPetInfo()).isTrue();
        assertThat(response.hasAccessibilityInfo()).isFalse();
        verify(spotPetInfoRepository).existsBySpotId(spotId);
        verify(spotAccessibilityInfoRepository).existsBySpotId(spotId);
    }
}
