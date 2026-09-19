package com.project.picngo.external.controller;

import com.project.picngo.auth.service.CustomUserDetails;
import com.project.picngo.spot.dto.PhotoAwardSyncStatusResponse;
import com.project.picngo.spot.producer.PhotoAwardSyncProducer;
import com.project.picngo.spot.service.PhotoAwardSyncStatusManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PhotoAwardAdminControllerTest {

    @Mock
    private PhotoAwardSyncProducer producer;

    @Mock
    private PhotoAwardSyncStatusManager statusManager;

    @Mock
    private CustomUserDetails adminUserDetails;

    @InjectMocks
    private PhotoAwardAdminController controller;

    @Test
    @DisplayName("법정동 코드로 특정 지역 동기화 요청 시 202 Accepted 및 큐 발행 검증")
    void syncAreaWithLdongRegnCd() {
        given(adminUserDetails.getId()).willReturn(1L);

        ResponseEntity<String> response = controller.syncArea(adminUserDetails, 26, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).contains("부산광역시").contains("26");
        verify(producer).sendAreaSync(26, 1L);
    }

    @Test
    @DisplayName("TourAPI 지역 코드로 요청 시 법정동 코드로 자동 변환되어 동기화된다")
    void syncAreaWithAreaCodeMapping() {
        given(adminUserDetails.getId()).willReturn(1L);

        // areaCode: 6 (부산) -> lDongRegnCd: 26
        ResponseEntity<String> response = controller.syncArea(adminUserDetails, null, 6);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).contains("부산광역시").contains("26");
        verify(producer).sendAreaSync(26, 1L);
    }

    @Test
    @DisplayName("지역 정보가 전혀 없으면 400 Bad Request를 반환한다")
    void syncAreaWithoutRegionFails() {
        ResponseEntity<String> response = controller.syncArea(adminUserDetails, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("전국 17개 지역 전체 동기화 요청 시 202 Accepted 및 큐 발행 검증")
    void syncAll() {
        given(adminUserDetails.getId()).willReturn(1L);

        ResponseEntity<String> response = controller.syncAll(adminUserDetails);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).contains("전국 17개 지역");
        verify(producer).sendAllSync(1L);
    }

    @Test
    @DisplayName("동기화 상태 조회 시 정상 반환 검증")
    void getSyncStatus() {
        PhotoAwardSyncStatusResponse mockStatus = PhotoAwardSyncStatusResponse.idle(null, null);
        given(statusManager.getStatus()).willReturn(mockStatus);

        ResponseEntity<PhotoAwardSyncStatusResponse> response = controller.getSyncStatus();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(mockStatus);
    }
}
