package com.project.picngo.spot.service;

import com.project.picngo.common.domain.SpotCategory;
import com.project.picngo.external.AccessibilityTourApiClient;
import com.project.picngo.external.dto.AccessibilityTourDetailResponse;
import com.project.picngo.external.dto.AccessibilityTourSyncListResponse;
import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.dto.AccessibilityTourSyncResultResponse;
import com.project.picngo.spot.repository.SpotAccessibilityInfoRepository;
import com.project.picngo.spot.repository.SpotRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AccessibilityTourSyncServiceTest {

    @Mock
    private AccessibilityTourApiClient apiClient;

    @Mock
    private SpotRepository spotRepository;

    @Mock
    private SpotAccessibilityInfoRepository accessibilityInfoRepository;

    @Mock
    private SpotAccessibilityInfoUpsertService upsertService;

    @InjectMocks
    private AccessibilityTourSyncService service;

    @Test
    void restrictsContentType39MatchingToCafeCategory() {
        var item = new AccessibilityTourSyncListResponse.Item("cafe-1", "39", "카페", "1");
        var response = new AccessibilityTourSyncListResponse(new AccessibilityTourSyncListResponse.Response(
                new AccessibilityTourSyncListResponse.Header("0000", "OK"),
                new AccessibilityTourSyncListResponse.Body(
                        new AccessibilityTourSyncListResponse.Items(List.of(item)), 500, 1, 1)));
        given(apiClient.getSyncList(39, 1, 500, null)).willReturn(response);
        given(spotRepository.findTourApiAddonTargets(List.of("cafe-1"), 39, SpotCategory.CAFE))
                .willReturn(List.of());

        AccessibilityTourSyncResultResponse result = service.syncMatchedSpots(39, 10);

        assertThat(result.matchedSpotCount()).isZero();
        verify(spotRepository).findTourApiAddonTargets(List.of("cafe-1"), 39, SpotCategory.CAFE);
    }

    @Test
    void syncsDetailOnlyForMatchedSpot() {
        given(apiClient.getSyncList(12, 1, 500, null))
                .willReturn(response(12, "100"));

        Spot spot = org.mockito.Mockito.mock(Spot.class);
        given(spot.getId()).willReturn(1L);
        given(spot.getTourContentId()).willReturn("100");
        given(spotRepository.findTourApiAddonTargets(List.of("100"), 12, SpotCategory.CAFE))
                .willReturn(List.of(spot));
        given(accessibilityInfoRepository.findExistingSpotIds(List.of(1L)))
                .willReturn(Set.of());

        AccessibilityTourDetailResponse.Item detail =
                org.mockito.Mockito.mock(AccessibilityTourDetailResponse.Item.class);
        given(apiClient.getDetail("100")).willReturn(detail);

        AccessibilityTourSyncResultResponse result = service.syncMatchedSpots(12, 10);

        assertThat(result.requestedDetailCount()).isEqualTo(1);
        assertThat(result.savedCount()).isEqualTo(1);
        verify(upsertService).upsert(spot, detail);
    }

    @Test
    void skipsDetailWhenAccessibilityInfoAlreadyExists() {
        given(apiClient.getSyncList(12, 1, 500, null))
                .willReturn(response(12, "100"));

        Spot spot = org.mockito.Mockito.mock(Spot.class);
        given(spot.getId()).willReturn(1L);
        given(spot.getTourContentId()).willReturn("100");
        given(spotRepository.findTourApiAddonTargets(List.of("100"), 12, SpotCategory.CAFE))
                .willReturn(List.of(spot));
        given(accessibilityInfoRepository.findExistingSpotIds(List.of(1L)))
                .willReturn(Set.of(1L));

        AccessibilityTourSyncResultResponse result = service.syncMatchedSpots(12, 10);

        assertThat(result.requestedDetailCount()).isZero();
        verifyNoInteractions(upsertService);
    }

    @Test
    void passesAreaCodeToListApi() {
        given(apiClient.getSyncList(12, 1, 500, 1))
                .willReturn(emptyResponse(12));

        service.syncMatchedSpots(12, 10, 1);

        verify(apiClient).getSyncList(12, 1, 500, 1);
    }

    @Test
    void continuesAfterDetailFailureAndCountsFailedItem() {
        given(apiClient.getSyncList(12, 1, 500, null))
                .willReturn(response(12, List.of("100", "200")));

        Spot failedSpot = org.mockito.Mockito.mock(Spot.class);
        given(failedSpot.getId()).willReturn(1L);
        given(failedSpot.getTourContentId()).willReturn("100");
        Spot successfulSpot = org.mockito.Mockito.mock(Spot.class);
        given(successfulSpot.getId()).willReturn(2L);
        given(successfulSpot.getTourContentId()).willReturn("200");
        given(spotRepository.findTourApiAddonTargets(
                List.of("100", "200"),
                12,
                SpotCategory.CAFE
        )).willReturn(List.of(failedSpot, successfulSpot));
        given(accessibilityInfoRepository.findExistingSpotIds(List.of(1L, 2L)))
                .willReturn(Set.of());
        given(apiClient.getDetail("100"))
                .willThrow(new IllegalStateException("API 호출 한도 초과"));
        AccessibilityTourDetailResponse.Item detail =
                org.mockito.Mockito.mock(AccessibilityTourDetailResponse.Item.class);
        given(apiClient.getDetail("200")).willReturn(detail);

        AccessibilityTourSyncResultResponse result = service.syncMatchedSpots(12, 10);

        assertThat(result.requestedDetailCount()).isEqualTo(2);
        assertThat(result.savedCount()).isEqualTo(1);
        assertThat(result.failedCount()).isEqualTo(1);
        verify(upsertService).upsert(successfulSpot, detail);
    }

    private AccessibilityTourSyncListResponse response(int contentTypeId, String contentId) {
        return response(contentTypeId, List.of(contentId));
    }

    private AccessibilityTourSyncListResponse response(
            int contentTypeId,
            List<String> contentIds
    ) {
        List<AccessibilityTourSyncListResponse.Item> items = contentIds.stream()
                .map(contentId -> new AccessibilityTourSyncListResponse.Item(
                        contentId,
                        String.valueOf(contentTypeId),
                        "관광지",
                        "1"
                ))
                .toList();
        return new AccessibilityTourSyncListResponse(
                new AccessibilityTourSyncListResponse.Response(
                        new AccessibilityTourSyncListResponse.Header("0000", "OK"),
                        new AccessibilityTourSyncListResponse.Body(
                                new AccessibilityTourSyncListResponse.Items(items),
                                500,
                                1,
                                items.size()
                        )
                )
        );
    }

    private AccessibilityTourSyncListResponse emptyResponse(int contentTypeId) {
        return new AccessibilityTourSyncListResponse(
                new AccessibilityTourSyncListResponse.Response(
                        new AccessibilityTourSyncListResponse.Header("0000", "OK"),
                        new AccessibilityTourSyncListResponse.Body(
                                new AccessibilityTourSyncListResponse.Items(List.of()),
                                500,
                                1,
                                0
                        )
                )
        );
    }
}
