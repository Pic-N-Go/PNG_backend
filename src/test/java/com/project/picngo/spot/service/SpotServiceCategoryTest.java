package com.project.picngo.spot.service;

import com.project.picngo.common.domain.SpotCategory;
import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.domain.enums.SpotStatus;
import com.project.picngo.spot.repository.SpotRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SpotServiceCategoryTest {

    @Mock
    private SpotRepository spotRepository;

    @InjectMocks
    private SpotService spotService;

    @Captor
    private ArgumentCaptor<List<SpotCategory>> categoriesCaptor;

    private Spot spot(Set<SpotCategory> categories) {
        return Spot.builder()
                .name("테스트 스팟")
                .address("서울특별시 종로구")
                .latitude(37.5)
                .longitude(127.0)
                .categories(categories)
                .status(SpotStatus.APPROVED)
                .build();
    }

    @Test
    @DisplayName("쉼표 다중 카테고리는 정확히 MOUNTAIN, SUNRISE_SUNSET 두 값으로 파싱돼 쿼리에 전달된다")
    void commaSeparated_passesExactCategories() {
        given(spotRepository.findAllByCategoriesAndStatusAndIsActiveTrue(any(), eq(SpotStatus.APPROVED), any()))
                .willReturn(new PageImpl<>(List.of(spot(Set.of(SpotCategory.MOUNTAIN, SpotCategory.SUNRISE_SUNSET)))));

        spotService.getSpots(List.of("MOUNTAIN,SUNRISE_SUNSET"), "latest", 0, 20);

        verify(spotRepository).findAllByCategoriesAndStatusAndIsActiveTrue(
                categoriesCaptor.capture(), eq(SpotStatus.APPROVED), any());
        assertThat(categoriesCaptor.getValue())
                .containsExactlyInAnyOrder(SpotCategory.MOUNTAIN, SpotCategory.SUNRISE_SUNSET);
    }

    @Test
    @DisplayName("유효/무효 혼합 요청은 유효한 카테고리(MOUNTAIN)만 쿼리에 전달된다")
    void mixedValidInvalid_usesOnlyValid() {
        given(spotRepository.findAllByCategoriesAndStatusAndIsActiveTrue(any(), eq(SpotStatus.APPROVED), any()))
                .willReturn(new PageImpl<>(List.of()));

        spotService.getSpots(List.of("MOUNTAIN", "INVALID_TEXT"), "latest", 0, 20);

        verify(spotRepository).findAllByCategoriesAndStatusAndIsActiveTrue(
                categoriesCaptor.capture(), eq(SpotStatus.APPROVED), any());
        assertThat(categoriesCaptor.getValue()).containsExactly(SpotCategory.MOUNTAIN);
    }

    @Test
    @DisplayName("전부 무효인 카테고리는 필터 없는 전체 조회로 폴백한다")
    void allInvalid_usesNoFilterQuery() {
        given(spotRepository.findAllByStatusAndIsActiveTrue(eq(SpotStatus.APPROVED), any()))
                .willReturn(new PageImpl<>(List.of()));

        // PORTRAIT는 삭제된 enum, INVALID_TEXT는 오타
        spotService.getSpots(List.of("PORTRAIT", "INVALID_TEXT"), "latest", 0, 20);

        verify(spotRepository).findAllByStatusAndIsActiveTrue(eq(SpotStatus.APPROVED), any());
        verify(spotRepository, never()).findAllByCategoriesAndStatusAndIsActiveTrue(any(), any(), any());
    }

    @Test
    @DisplayName("카테고리가 비어있는 스팟의 응답 카테고리는 정확히 [ETC]다")
    void spotWithEmptyCategories_returnsEtc() {
        given(spotRepository.findAllByStatusAndIsActiveTrue(eq(SpotStatus.APPROVED), any()))
                .willReturn(new PageImpl<>(List.of(spot(Set.of()))));

        var response = spotService.getSpots(List.of(), "latest", 0, 20);

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).categories()).containsExactly("ETC");
    }
}
