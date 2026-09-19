package com.project.picngo.external;

import com.project.picngo.external.dto.AccessibilityTourDetailResponse;
import com.project.picngo.external.dto.AccessibilityTourSyncListResponse;
import com.project.picngo.external.dto.PetTourDetailResponse;
import com.project.picngo.external.dto.PetTourSyncListResponse;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TourApiClientResponseValidationTest {

    private final PetTourApiClient petClient = new PetTourApiClient(
            WebClient.builder(),
            "test-key",
            "http://localhost"
    );

    private final AccessibilityTourApiClient accessibilityClient =
            new AccessibilityTourApiClient(
                    WebClient.builder(),
                    "test-key",
                    "http://localhost"
            );

    @Test
    void rejectsPetListResponseWithoutHeader() {
        PetTourSyncListResponse response = new PetTourSyncListResponse(
                new PetTourSyncListResponse.Response(null, null)
        );

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                petClient,
                "validateResponse",
                response
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("반려동물 목록 API 에러");
    }

    @Test
    void rejectsPetDetailResponseWithoutResultCode() {
        PetTourDetailResponse response = new PetTourDetailResponse(
                new PetTourDetailResponse.Response(
                        new PetTourDetailResponse.Header(null, "missing code"),
                        null
                )
        );

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                petClient,
                "validateDetailResponse",
                response,
                "100"
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("반려동물 상세 API 에러");
    }

    @Test
    void rejectsAccessibilityListResponseWithoutHeader() {
        AccessibilityTourSyncListResponse response = new AccessibilityTourSyncListResponse(
                new AccessibilityTourSyncListResponse.Response(null, null)
        );

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                accessibilityClient,
                "validateListResponse",
                response
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("무장애 목록 API 에러");
    }

    @Test
    void rejectsAccessibilityDetailResponseWithoutResultCode() {
        AccessibilityTourDetailResponse response = new AccessibilityTourDetailResponse(
                new AccessibilityTourDetailResponse.Response(
                        new AccessibilityTourDetailResponse.Header(null, "missing code"),
                        null
                )
        );

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                accessibilityClient,
                "validateDetailResponse",
                response,
                "100"
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("무장애 상세 API");
    }
}
