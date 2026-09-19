package com.project.picngo.external.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AccessibilityTourDetailResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void treatsEmptyStringItemsAsEmptyList() throws Exception {
        String json = """
                {
                  "response": {
                    "header": {"resultCode": "0000", "resultMsg": "OK"},
                    "body": {"items": "", "totalCount": 0}
                  }
                }
                """;

        AccessibilityTourDetailResponse response =
                objectMapper.readValue(json, AccessibilityTourDetailResponse.class);

        assertThat(response.response().body().items().safeItems()).isEmpty();
    }
}
