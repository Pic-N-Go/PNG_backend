package com.project.picngo.spot.service;

import com.project.picngo.common.domain.SpotCategory;
import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.domain.enums.SpotSource;
import com.project.picngo.spot.domain.enums.SpotStatus;
import com.project.picngo.spot.dto.FestivalResponse;
import com.project.picngo.spot.repository.SpotRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class FestivalServiceTest {

    @Mock
    private SpotRepository spotRepository;

    @InjectMocks
    private FestivalService festivalService;

    @Test
    @DisplayName("진행 중인 축제(ONGOING) 조회 시 시작일/종료일 기준 필터링 쿼리가 실행된다")
    void getFestivalsOngoing() {
        LocalDate today = LocalDate.of(2026, 8, 24);
        Spot ongoingFestival = Spot.builder()
                .name("2026 여름 바다 축제")
                .address("부산광역시 해운대구")
                .contentTypeId(15)
                .eventStartDate(LocalDate.of(2026, 8, 1))
                .eventEndDate(LocalDate.of(2026, 8, 31))
                .categories(Set.of(SpotCategory.FESTIVAL, SpotCategory.BEACH))
                .status(SpotStatus.APPROVED)
                .source(SpotSource.TOUR_API)
                .build();

        Page<Spot> spotPage = new PageImpl<>(List.of(ongoingFestival), PageRequest.of(0, 20), 1);

        given(spotRepository.findFestivals(
                eq(SpotCategory.FESTIVAL),
                eq(SpotStatus.APPROVED),
                eq(today),
                eq(today),
                any()
        )).willReturn(spotPage);

        Page<FestivalResponse> result = festivalService.getFestivals("ONGOING", today, 0, 20);

        assertThat(result.getContent()).hasSize(1);
        FestivalResponse response = result.getContent().get(0);
        assertThat(response.getName()).isEqualTo("2026 여름 바다 축제");
        assertThat(response.getProgressStatus()).isEqualTo("ONGOING");
        assertThat(response.getCategories()).contains("FESTIVAL", "BEACH");
    }

    @Test
    @DisplayName("축제 상세 조회 성공")
    void getFestivalByIdSuccess() {
        Spot festival = Spot.builder()
                .name("가을 국화 축제")
                .address("전라남도 함평군")
                .contentTypeId(15)
                .eventStartDate(LocalDate.of(2026, 10, 1))
                .eventEndDate(LocalDate.of(2026, 10, 20))
                .categories(Set.of(SpotCategory.FESTIVAL, SpotCategory.FLOWER))
                .status(SpotStatus.APPROVED)
                .isActive(true)
                .source(SpotSource.TOUR_API)
                .build();

        given(spotRepository.findById(100L)).willReturn(Optional.of(festival));

        FestivalResponse response = festivalService.getFestivalById(100L);

        assertThat(response.getName()).isEqualTo("가을 국화 축제");
        assertThat(response.getCategories()).contains("FESTIVAL", "FLOWER");
    }
}
