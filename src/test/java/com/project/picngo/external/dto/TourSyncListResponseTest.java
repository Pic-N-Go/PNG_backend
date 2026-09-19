package com.project.picngo.external.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TourSyncListResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void petListTreatsEmptyStringItemsAsEmptyList() throws Exception {
        PetTourSyncListResponse response = objectMapper.readValue(
                emptyItemsResponse(),
                PetTourSyncListResponse.class
        );

        assertThat(response.response().body().items().safeItems()).isEmpty();
    }

    @Test
    void accessibilityListTreatsEmptyStringItemsAsEmptyList() throws Exception {
        AccessibilityTourSyncListResponse response = objectMapper.readValue(
                emptyItemsResponse(),
                AccessibilityTourSyncListResponse.class
        );

        assertThat(response.response().body().items().safeItems()).isEmpty();
    }

    private String emptyItemsResponse() {
        return """
                {
                  "response": {
                    "header": {"resultCode": "0000", "resultMsg": "OK"},
                    "body": {
                      "items": "",
                      "numOfRows": 500,
                      "pageNo": 1,
                      "totalCount": 0
                    }
                  }
                }
                """;
    }
}
