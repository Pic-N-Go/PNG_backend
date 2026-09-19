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

    @Test
    void petListAcceptsSingleItemObject() throws Exception {
        PetTourSyncListResponse response = objectMapper.readValue(
                singleItemResponse(),
                PetTourSyncListResponse.class
        );

        assertThat(response.response().body().items().safeItems())
                .singleElement()
                .extracting(PetTourSyncListResponse.Item::contentid)
                .isEqualTo("100");
    }

    @Test
    void accessibilityListAcceptsSingleItemObject() throws Exception {
        AccessibilityTourSyncListResponse response = objectMapper.readValue(
                singleItemResponse(),
                AccessibilityTourSyncListResponse.class
        );

        assertThat(response.response().body().items().safeItems())
                .singleElement()
                .extracting(AccessibilityTourSyncListResponse.Item::contentid)
                .isEqualTo("100");
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

    private String singleItemResponse() {
        return """
                {
                  "response": {
                    "header": {"resultCode": "0000", "resultMsg": "OK"},
                    "body": {
                      "items": {
                        "item": {
                          "contentid": "100",
                          "contenttypeid": "12",
                          "title": "관광지",
                          "showflag": "1"
                        }
                      },
                      "numOfRows": 1,
                      "pageNo": 1,
                      "totalCount": 1
                    }
                  }
                }
                """;
    }
}
