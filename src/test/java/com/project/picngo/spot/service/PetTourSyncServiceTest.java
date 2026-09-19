package com.project.picngo.spot.service;

import com.project.picngo.external.PetTourApiClient;
import com.project.picngo.external.dto.PetTourDetailResponse;
import com.project.picngo.external.dto.PetTourSyncListResponse;
import com.project.picngo.spot.domain.Spot;
import com.project.picngo.common.domain.SpotCategory;
import com.project.picngo.spot.dto.PetTourMatchStatusResponse;
import com.project.picngo.spot.dto.PetTourSyncResultResponse;
import com.project.picngo.spot.repository.SpotRepository;
import com.project.picngo.spot.repository.SpotPetInfoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PetTourSyncServiceTest {

    @Mock
    private PetTourApiClient petTourApiClient;

    @Mock
    private SpotRepository spotRepository;

    @Mock
    private SpotPetInfoRepository spotPetInfoRepository;

    @Mock
    private SpotPetInfoUpsertService spotPetInfoUpsertService;

    @InjectMocks
    private PetTourSyncService petTourSyncService;

    @Test
    void previewsOnlyExistingTourContentIdsAcrossPages() {
        given(petTourApiClient.getSyncList(12, 1, 500))
                .willReturn(response(1, 501, List.of(
                        item("100", "1"),
                        item("200", "1"),
                        item("hidden", "0")
                )));
        given(petTourApiClient.getSyncList(12, 2, 500))
                .willReturn(response(2, 501, List.of(item("300", "1"))));

        Spot first = org.mockito.Mockito.mock(Spot.class);
        Spot second = org.mockito.Mockito.mock(Spot.class);
        given(spotRepository.findTourApiAddonTargets(List.of("100", "200"), 12, SpotCategory.CAFE))
                .willReturn(List.of(first));
        given(spotRepository.findTourApiAddonTargets(List.of("300"), 12, SpotCategory.CAFE))
                .willReturn(List.of(second));

        PetTourMatchStatusResponse result = petTourSyncService.getMatchStatus(12);

        assertThat(result.contentTypeId()).isEqualTo(12);
        assertThat(result.apiTotalCount()).isEqualTo(501);
        assertThat(result.receivedCount()).isEqualTo(4);
        assertThat(result.matchedSpotCount()).isEqualTo(2);
        verify(petTourApiClient).getSyncList(12, 2, 500);
    }

    @Test
    void syncsDetailsOnlyForMatchedSpots() {
        given(petTourApiClient.getSyncList(12, 1, 500, null))
                .willReturn(response(1, 2, List.of(item("100", "1"), item("200", "1"))));

        Spot matched = org.mockito.Mockito.mock(Spot.class);
        given(matched.getId()).willReturn(1L);
        given(matched.getTourContentId()).willReturn("100");
        given(spotRepository.findTourApiAddonTargets(List.of("100", "200"), 12, SpotCategory.CAFE))
                .willReturn(List.of(matched));
        given(spotPetInfoRepository.findExistingSpotIds(List.of(1L)))
                .willReturn(Set.of());

        PetTourDetailResponse.Item detail = new PetTourDetailResponse.Item(
                "100", "", "일부구역 동반가능", "", "", "안내",
                "", "전 견종 동반 가능", "", "목줄 착용"
        );
        given(petTourApiClient.getDetail("100")).willReturn(detail);

        PetTourSyncResultResponse result = petTourSyncService.syncMatchedSpots(12, 10);

        assertThat(result.matchedSpotCount()).isEqualTo(1);
        assertThat(result.requestedDetailCount()).isEqualTo(1);
        assertThat(result.savedCount()).isEqualTo(1);
        verify(spotPetInfoUpsertService).upsert(matched, detail);
    }

    @Test
    void skipsDetailApiForSpotsThatAlreadyHavePetInfo() {
        given(petTourApiClient.getSyncList(12, 1, 500, null))
                .willReturn(response(1, 1, List.of(item("100", "1"))));

        Spot alreadySynced = org.mockito.Mockito.mock(Spot.class);
        given(alreadySynced.getId()).willReturn(1L);
        given(alreadySynced.getTourContentId()).willReturn("100");
        given(spotRepository.findTourApiAddonTargets(List.of("100"), 12, SpotCategory.CAFE))
                .willReturn(List.of(alreadySynced));
        given(spotPetInfoRepository.findExistingSpotIds(List.of(1L)))
                .willReturn(Set.of(1L));

        PetTourSyncResultResponse result = petTourSyncService.syncMatchedSpots(12, 10);

        assertThat(result.matchedSpotCount()).isEqualTo(1);
        assertThat(result.requestedDetailCount()).isZero();
        assertThat(result.savedCount()).isZero();
        org.mockito.Mockito.verifyNoInteractions(spotPetInfoUpsertService);
        org.mockito.Mockito.verify(petTourApiClient, org.mockito.Mockito.never()).getDetail("100");
    }

    @Test
    void restrictsContentType39MatchingToCafeCategory() {
        given(petTourApiClient.getSyncList(39, 1, 500, null))
                .willReturn(response(1, 1, List.of(item("cafe-1", "1"))));
        given(spotRepository.findTourApiAddonTargets(List.of("cafe-1"), 39, SpotCategory.CAFE))
                .willReturn(List.of());

        PetTourSyncResultResponse result = petTourSyncService.syncMatchedSpots(39, 10);

        assertThat(result.matchedSpotCount()).isZero();
        verify(spotRepository).findTourApiAddonTargets(List.of("cafe-1"), 39, SpotCategory.CAFE);
    }

    @Test
    void passesLegalRegionCodeToListApi() {
        given(petTourApiClient.getSyncList(12, 1, 500, 11))
                .willReturn(response(1, 0, List.of()));

        petTourSyncService.syncMatchedSpots(12, 10, 11);

        verify(petTourApiClient).getSyncList(12, 1, 500, 11);
    }

    @Test
    void propagatesDetailApiFailureForRabbitRetry() {
        given(petTourApiClient.getSyncList(12, 1, 500, null))
                .willReturn(response(1, 1, List.of(item("100", "1"))));

        Spot matched = org.mockito.Mockito.mock(Spot.class);
        given(matched.getId()).willReturn(1L);
        given(matched.getTourContentId()).willReturn("100");
        given(spotRepository.findTourApiAddonTargets(List.of("100"), 12, SpotCategory.CAFE))
                .willReturn(List.of(matched));
        given(spotPetInfoRepository.findExistingSpotIds(List.of(1L))).willReturn(Set.of());
        given(petTourApiClient.getDetail("100"))
                .willThrow(new IllegalStateException("API 호출 한도 초과"));

        assertThatThrownBy(() -> petTourSyncService.syncMatchedSpots(12, 10))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("API 호출 한도 초과");
    }

    private PetTourSyncListResponse response(
            int pageNo,
            int totalCount,
            List<PetTourSyncListResponse.Item> items
    ) {
        return new PetTourSyncListResponse(new PetTourSyncListResponse.Response(
                new PetTourSyncListResponse.Header("0000", "OK"),
                new PetTourSyncListResponse.Body(
                        new PetTourSyncListResponse.Items(items),
                        500,
                        pageNo,
                        totalCount
                )
        ));
    }

    private PetTourSyncListResponse.Item item(String contentId, String showflag) {
        return new PetTourSyncListResponse.Item(contentId, "12", "관광지", showflag);
    }
}
