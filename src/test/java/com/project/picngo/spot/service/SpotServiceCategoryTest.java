package com.project.picngo.spot.service;

import com.project.picngo.common.domain.SpotCategory;
import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.domain.enums.SpotStatus;
import com.project.picngo.spot.dto.MapBoundsRequest;
import com.project.picngo.spot.repository.SpotRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class SpotServiceCategoryTest {

    @Mock
    private SpotRepository spotRepository;

    @InjectMocks
    private SpotService spotService;

    @Test
    @DisplayName("쉼표로 구분된 다중 카테고리 요청 시 정상적으로 파싱되어 쿼리로 전달된다")
    void parseCategories_commaSeparated_success() {
        // given
        Spot spot = Spot.builder()
                .name("성산일출봉")
                .address("제주도")
                .latitude(33.45)
                .longitude(126.94)
                .categories(Set.of(SpotCategory.MOUNTAIN, SpotCategory.SUNRISE_SUNSET))
                .status(SpotStatus.APPROVED)
                .build();

        given(spotRepository.findAllByCategoriesAndStatusAndIsActiveTrue(any(), eq(SpotStatus.APPROVED), any()))
                .willReturn(new PageImpl<>(List.of(spot)));

        // when
        var response = spotService.getSpots(List.of("MOUNTAIN,SUNRISE_SUNSET"), "latest", 0, 20);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).categories()).containsExactly("MOUNTAIN", "SUNRISE_SUNSET");
    }

    @Test
    @DisplayName("삭제되거나 유효하지 않은 카테고리 요청 시 에러 없이 스킵 처리된다")
    void parseCategories_invalidCategory_skippedWithoutError() {
        // given
        given(spotRepository.findAllByStatusAndIsActiveTrue(eq(SpotStatus.APPROVED), any()))
                .willReturn(new PageImpl<>(List.of()));

        // when (PORTRAIT는 삭제된 enum, INVALID는 오타)
        var response = spotService.getSpots(List.of("PORTRAIT", "INVALID_TEXT"), "latest", 0, 20);

        // then - 유효한 카테고리가 없으므로 전체 조회로 폴백
        assertThat(response).isNotNull();
    }
}
