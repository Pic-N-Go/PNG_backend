package com.project.picngo.spot.service;

import com.project.picngo.external.TourApiClient;
import com.project.picngo.external.dto.TourApiResponse;
import com.project.picngo.external.dto.TourApiResponse.Body;
import com.project.picngo.external.dto.TourApiResponse.Header;
import com.project.picngo.external.dto.TourApiResponse.Item;
import com.project.picngo.external.dto.TourApiResponse.Items;
import com.project.picngo.external.dto.TourApiResponse.Response;
import com.project.picngo.spot.repository.SpotRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TourApiSyncServiceTest {

    @Mock
    private TourApiClient tourApiClient;

    @Mock
    private SpotUpsertService spotUpsertService;

    @Mock
    private SpotRepository spotRepository;

    @Mock
    private TourApiSyncStatusManager syncStatusManager;

    @InjectMocks
    private TourApiSyncService tourApiSyncService;

    private TourApiResponse createMockResponse(List<Item> items, int totalCount) {
        Header header = new Header("0000", "OK");
        Items itemsObj = new Items(items);
        Body body = new Body(itemsObj, 100, 1, totalCount);
        Response response = new Response(header, body);
        return new TourApiResponse(response);
    }

    private Item createItem(String contentId, String contentTypeId, String title) {
        return new Item(
                contentId, contentTypeId, title, "주소", null, "12345",
                "126.97", "37.56", null, null, null,
                null, null, null, null, null,
                null, null, null, null, null,
                null, null, null, null, null,
                null, null, null, null, null
        );
    }

    @Test
    @DisplayName("지역 동기화 시 4개 타입(관광지 12, 문화시설 14, 축제 15, 카페 39)이 모두 호출된다")
    void syncAllTargetTypesForArea() {
        int areaCode = 2; // 인천

        TourApiResponse emptyResponse = createMockResponse(Collections.emptyList(), 0);

        // 12 (관광지)
        given(tourApiClient.getAreaBasedListRaw(12, areaCode, 1, 100)).willReturn(emptyResponse);
        // 14 (문화시설)
        given(tourApiClient.getAreaBasedListRaw(14, areaCode, 1, 100)).willReturn(emptyResponse);
        // 15 (축제)
        given(tourApiClient.getFestivalList(anyString(), eq(areaCode), isNull(), eq(1), eq(100))).willReturn(emptyResponse);
        // 39 (카페 - FD050100)
        given(tourApiClient.getAreaBasedListRaw(39, areaCode, "FD050100", 1, 100)).willReturn(emptyResponse);

        int totalSaved = tourApiSyncService.sync(areaCode);

        assertThat(totalSaved).isEqualTo(0);

        verify(tourApiClient).getAreaBasedListRaw(12, areaCode, 1, 100);
        verify(tourApiClient).getAreaBasedListRaw(14, areaCode, 1, 100);
        verify(tourApiClient).getFestivalList(anyString(), eq(areaCode), isNull(), eq(1), eq(100));
        verify(tourApiClient).getAreaBasedListRaw(39, areaCode, "FD050100", 1, 100);
    }

    @Test
    @DisplayName("이미 DB에 존재하는 스팟은 상세 조회를 스킵하고 저장 카운트를 증가시키지 않는다")
    void skipAlreadyExistingSpots() {
        int areaCode = 2;
        Item item = createItem("1001", "12", "인천 차이나타운");
        TourApiResponse response = createMockResponse(List.of(item), 1);

        given(tourApiClient.getAreaBasedListRaw(12, areaCode, 1, 100)).willReturn(response);
        given(tourApiClient.getAreaBasedListRaw(14, areaCode, 1, 100)).willReturn(createMockResponse(Collections.emptyList(), 0));
        given(tourApiClient.getFestivalList(anyString(), eq(areaCode), isNull(), eq(1), eq(100))).willReturn(createMockResponse(Collections.emptyList(), 0));
        given(tourApiClient.getAreaBasedListRaw(39, areaCode, "FD050100", 1, 100)).willReturn(createMockResponse(Collections.emptyList(), 0));

        // 이미 존재하는 ID로 반환
        given(spotRepository.findExistingTourContentIds(List.of("1001"))).willReturn(Set.of("1001"));

        int totalSaved = tourApiSyncService.sync(areaCode);

        assertThat(totalSaved).isEqualTo(0);
        // 이미 존재하므로 상세 조회 및 upsertSpot 미호출 검증
        verify(tourApiClient, never()).getDetailCommon(anyString());
        verify(tourApiClient, never()).getDetailIntro(anyString(), anyInt());
        verify(tourApiClient, never()).getDetailImages(anyString());
        verify(spotUpsertService, never()).upsertSpot(any(), any(), any(), any());
    }

    @Test
    @DisplayName("신규 스팟인 경우 상세 조회를 거쳐 upsertSpot이 호출되고 saved 카운트가 증가한다")
    void saveNewSpotSuccessfully() {
        int areaCode = 2;
        Item item = createItem("1002", "12", "월미도");
        TourApiResponse response = createMockResponse(List.of(item), 1);

        given(tourApiClient.getAreaBasedListRaw(12, areaCode, 1, 100)).willReturn(response);
        given(tourApiClient.getAreaBasedListRaw(14, areaCode, 1, 100)).willReturn(createMockResponse(Collections.emptyList(), 0));
        given(tourApiClient.getFestivalList(anyString(), eq(areaCode), isNull(), eq(1), eq(100))).willReturn(createMockResponse(Collections.emptyList(), 0));
        given(tourApiClient.getAreaBasedListRaw(39, areaCode, "FD050100", 1, 100)).willReturn(createMockResponse(Collections.emptyList(), 0));

        given(spotRepository.findExistingTourContentIds(List.of("1002"))).willReturn(Collections.emptySet());
        // 상세 조회가 하나라도 응답하면 정상 저장 대상이다 (전부 비어야만 실패로 본다)
        given(tourApiClient.getDetailCommon("1002")).willReturn(createItem("1002", "12", "월미도"));
        given(spotUpsertService.upsertSpot(any(), any(), any(), any())).willReturn(true);

        int totalSaved = tourApiSyncService.sync(areaCode);

        assertThat(totalSaved).isEqualTo(1);
        verify(tourApiClient).getDetailCommon("1002");
        verify(tourApiClient).getDetailIntro("1002", 12);
        verify(tourApiClient).getDetailImages("1002");
        verify(spotUpsertService).upsertSpot(eq(item), any(), any(), any());
    }

    @Test
    @DisplayName("상세 조회 3종이 모두 비면 저장하지 않는다 - API 장애를 성공으로 집계하지 않기 위함")
    void skipSpotWhenAllDetailFetchesFail() {
        int areaCode = 2;
        Item item = createItem("1003", "12", "송도 센트럴파크");
        TourApiResponse response = createMockResponse(List.of(item), 1);

        given(tourApiClient.getAreaBasedListRaw(12, areaCode, 1, 100)).willReturn(response);
        given(tourApiClient.getAreaBasedListRaw(14, areaCode, 1, 100)).willReturn(createMockResponse(Collections.emptyList(), 0));
        given(tourApiClient.getFestivalList(anyString(), eq(areaCode), isNull(), eq(1), eq(100))).willReturn(createMockResponse(Collections.emptyList(), 0));
        given(tourApiClient.getAreaBasedListRaw(39, areaCode, "FD050100", 1, 100)).willReturn(createMockResponse(Collections.emptyList(), 0));

        given(spotRepository.findExistingTourContentIds(List.of("1003"))).willReturn(Collections.emptySet());

        // 상세 조회 클라이언트는 실패해도 예외를 던지지 않는다 - null과 빈 목록으로 돌아온다
        given(tourApiClient.getDetailCommon("1003")).willReturn(null);
        given(tourApiClient.getDetailIntro("1003", 12)).willReturn(null);
        given(tourApiClient.getDetailImages("1003")).willReturn(Collections.emptyList());

        int totalSaved = tourApiSyncService.sync(areaCode);

        assertThat(totalSaved).isEqualTo(0);
        verify(spotUpsertService, never()).upsertSpot(any(), any(), any(), any());
    }

    @Test
    @DisplayName("상세 조회가 일부만 비면 원래 상세가 없는 스팟일 수 있으므로 그대로 저장한다")
    void saveSpotWhenOnlySomeDetailFetchesAreEmpty() {
        int areaCode = 2;
        Item item = createItem("1004", "12", "강화 석모도");
        TourApiResponse response = createMockResponse(List.of(item), 1);

        given(tourApiClient.getAreaBasedListRaw(12, areaCode, 1, 100)).willReturn(response);
        given(tourApiClient.getAreaBasedListRaw(14, areaCode, 1, 100)).willReturn(createMockResponse(Collections.emptyList(), 0));
        given(tourApiClient.getFestivalList(anyString(), eq(areaCode), isNull(), eq(1), eq(100))).willReturn(createMockResponse(Collections.emptyList(), 0));
        given(tourApiClient.getAreaBasedListRaw(39, areaCode, "FD050100", 1, 100)).willReturn(createMockResponse(Collections.emptyList(), 0));

        given(spotRepository.findExistingTourContentIds(List.of("1004"))).willReturn(Collections.emptySet());

        // 개요는 왔지만 소개·이미지가 없는 스팟 - 장애가 아니라 원래 데이터가 그런 경우다
        given(tourApiClient.getDetailCommon("1004")).willReturn(createItem("1004", "12", "강화 석모도"));
        given(tourApiClient.getDetailIntro("1004", 12)).willReturn(null);
        given(tourApiClient.getDetailImages("1004")).willReturn(Collections.emptyList());
        given(spotUpsertService.upsertSpot(any(), any(), any(), any())).willReturn(true);

        int totalSaved = tourApiSyncService.sync(areaCode);

        assertThat(totalSaved).isEqualTo(1);
        verify(spotUpsertService).upsertSpot(eq(item), any(), isNull(), anyList());
    }

    @Test
    @DisplayName("TourAPI 호출 중 예외 발생 시 침묵하지 않고 예외가 전파된다")
    void propagateExceptionOnApiFailure() {
        int areaCode = 2;
        given(tourApiClient.getAreaBasedListRaw(12, areaCode, 1, 100))
                .willThrow(new IllegalStateException("TourAPI areaBasedList 에러 응답: [22] LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDED"));

        assertThatThrownBy(() -> tourApiSyncService.sync(areaCode))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("[22] LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDED");
    }
}
