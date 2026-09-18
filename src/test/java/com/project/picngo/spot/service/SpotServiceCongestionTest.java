package com.project.picngo.spot.service;

import com.project.picngo.external.dto.CongestionApiResponse.CongestionItem;
import com.project.picngo.external.service.CongestionCacheService;
import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.dto.SpotCongestionResponse;
import com.project.picngo.spot.repository.SpotRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class SpotServiceCongestionTest {

    @Mock
    private SpotRepository spotRepository;

    @Mock
    private CongestionCacheService congestionCacheService;

    @InjectMocks
    private SpotService spotService;

    @Test
    @DisplayName("스팟의 30일 집중률 데이터 중 최저 집중률 일자(bestCleanDay) 및 선택 일자 정보를 정확히 계산한다")
    void getSpotCongestion_calculatesBestCleanDayAndTargetDay() {
        // given
        Long spotId = 100L;
        Spot spot = Spot.builder()
                .name("간현관광지")
                .address("강원특별자치도 원주시 지정면 소금산길 12")
                .latitude(37.368)
                .longitude(127.828)
                .build();
        ReflectionTestUtils.setField(spot, "id", spotId);

        given(spotRepository.findById(spotId)).willReturn(Optional.of(spot));

        List<CongestionItem> items = List.of(
                new CongestionItem("20260925", "51", "강원특별자치도", "51130", "원주시", "간현관광지", 64.5),
                new CongestionItem("20260926", "51", "강원특별자치도", "51130", "원주시", "간현관광지", 32.1), // 최저 혼잡일
                new CongestionItem("20260927", "51", "강원특별자치도", "51130", "원주시", "간현관광지", 78.9)
        );
        given(congestionCacheService.getCachedCongestion(spot)).willReturn(items);

        // when (9월 25일 조회)
        SpotCongestionResponse response = spotService.getSpotCongestion(spotId, LocalDate.of(2026, 9, 25));

        // then
        assertThat(response).isNotNull();
        assertThat(response.hasData()).isTrue();
        assertThat(response.spotId()).isEqualTo(spotId);
        assertThat(response.spotName()).isEqualTo("간현관광지");
        assertThat(response.matchedAttractionName()).isEqualTo("간현관광지");
        assertThat(response.days()).hasSize(3);

        // 최저 집중률 일자: 2026-09-26 (32.1% 여유)
        assertThat(response.bestCleanDay()).isNotNull();
        assertThat(response.bestCleanDay().date()).isEqualTo("2026-09-26");
        assertThat(response.bestCleanDay().rate()).isEqualTo(32.1);
        assertThat(response.bestCleanDay().level()).isEqualTo("RELAXED");
        assertThat(response.bestCleanDay().levelLabel()).isEqualTo("여유");

        // 타겟 일자: 2026-09-25 (64.5% 혼잡)
        assertThat(response.targetDay()).isNotNull();
        assertThat(response.targetDay().date()).isEqualTo("2026-09-25");
        assertThat(response.targetDay().rate()).isEqualTo(64.5);
        assertThat(response.targetDay().level()).isEqualTo("CROWDED");
        assertThat(response.targetDay().levelLabel()).isEqualTo("혼잡");
    }

    @Test
    @DisplayName("공사 빅데이터 명소에 매칭되지 않아 빈 결과인 경우 hasData=false 응답을 반환한다")
    void getSpotCongestion_returnsEmptyWhenNoData() {
        // given
        Long spotId = 200L;
        Spot spot = Spot.builder()
                .name("알려지지 않은 작은 쉼터")
                .address("서울특별시 종로구 어딘가")
                .build();
        ReflectionTestUtils.setField(spot, "id", spotId);

        given(spotRepository.findById(spotId)).willReturn(Optional.of(spot));
        given(congestionCacheService.getCachedCongestion(spot)).willReturn(List.of());

        // when
        SpotCongestionResponse response = spotService.getSpotCongestion(spotId, null);

        // then
        assertThat(response).isNotNull();
        assertThat(response.hasData()).isFalse();
        assertThat(response.bestCleanDay()).isNull();
        assertThat(response.targetDay()).isNull();
        assertThat(response.days()).isEmpty();
    }
}
