package com.project.picngo.external.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PetTourDetailResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void acceptsSingleItemObject() throws Exception {
        String json = """
                {
                  "response": {
                    "header": {"resultCode": "0000", "resultMsg": "OK"},
                    "body": {
                      "items": {
                        "item": {
                          "contentid": "100",
                          "acmpyTypeCd": "일부구역 동반가능"
                        }
                      },
                      "totalCount": 1
                    }
                  }
                }
                """;

        PetTourDetailResponse response =
                objectMapper.readValue(json, PetTourDetailResponse.class);

        assertThat(response.response().body().items().safeItems())
                .singleElement()
                .extracting(PetTourDetailResponse.Item::contentid)
                .isEqualTo("100");
    }
}
