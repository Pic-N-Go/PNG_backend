package com.project.picngo.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.picngo.external.dto.TourApiIntroResponse;
import com.project.picngo.external.dto.TourApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TourApiResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("TourApiResponse: 신규 lclsSystm3 필드가 있으면 getEffectiveCategoryCode가 lclsSystm3을 반환한다")
    void testEffectiveCategoryCodeWithLclsSystm3() throws Exception {
        String json = """
                {
                  "response": {
                    "header": { "resultCode": "0000", "resultMsg": "OK" },
                    "body": {
                      "items": {
                        "item": [
                          {
                            "contentid": "127974",
                            "contenttypeid": "12",
                            "title": "을숙도 공원",
                            "addr1": "부산광역시 사하구 낙동남로 1240",
                            "lclsSystm1": "NA",
                            "lclsSystm2": "NA04",
                            "lclsSystm3": "NA040500",
                            "lDongRegnCd": "26",
                            "lDongSignguCd": "380"
                          }
                        ]
                      },
                      "numOfRows": 10,
                      "pageNo": 1,
                      "totalCount": 1
                    }
                  }
                }
                """;

        TourApiResponse response = objectMapper.readValue(json, TourApiResponse.class);
        assertThat(response.response().body().items().item()).hasSize(1);
        TourApiResponse.Item item = response.response().body().items().item().get(0);

        assertThat(item.contentid()).isEqualTo("127974");
        assertThat(item.getContentTypeIdOrNull()).isEqualTo(12);
        assertThat(item.lclsSystm3()).isEqualTo("NA040500");
        assertThat(item.lDongRegnCd()).isEqualTo("26");
        assertThat(item.getEffectiveCategoryCode()).isEqualTo("NA040500");
    }

    @Test
    @DisplayName("TourApiResponse: 구버전 cat3만 있는 경우 getEffectiveCategoryCode가 cat3을 반환한다")
    void testEffectiveCategoryCodeWithCat3Fallback() throws Exception {
        String json = """
                {
                  "response": {
                    "header": { "resultCode": "0000", "resultMsg": "OK" },
                    "body": {
                      "items": {
                        "item": [
                          {
                            "contentid": "127974",
                            "title": "을숙도 공원",
                            "cat3": "A02020700"
                          }
                        ]
                      },
                      "numOfRows": 10,
                      "pageNo": 1,
                      "totalCount": 1
                    }
                  }
                }
                """;

        TourApiResponse response = objectMapper.readValue(json, TourApiResponse.class);
        TourApiResponse.Item item = response.response().body().items().item().get(0);

        assertThat(item.cat3()).isEqualTo("A02020700");
        assertThat(item.getEffectiveCategoryCode()).isEqualTo("A02020700");
    }

    @Test
    @DisplayName("TourApiIntroResponse: 관광지, 문화시설, 음식점의 서로 다른 필드명을 통합 메서드로 추출한다")
    void testIntroItemEffectiveGetters() throws Exception {
        // 1. 문화시설 (14)
        String cultureJson = """
                {
                  "response": {
                    "body": {
                      "items": {
                        "item": [
                          {
                            "contentid": "129789",
                            "usetimeculture": "09:30 ~ 17:30",
                            "restdateculture": "매주 월요일",
                            "parkingculture": "지하주차장 무료",
                            "infocenterculture": "042-870-1200",
                            "chkbabycarriageculture": "유모차 대여 가능",
                            "chkpetculture": "반려동물 동반 불가"
                          }
                        ]
                      }
                    }
                  }
                }
                """;

        TourApiIntroResponse cultureRes = objectMapper.readValue(cultureJson, TourApiIntroResponse.class);
        TourApiIntroResponse.IntroItem cultureItem = cultureRes.response().body().items().item().get(0);

        assertThat(cultureItem.getEffectiveUsetime()).isEqualTo("09:30 ~ 17:30");
        assertThat(cultureItem.getEffectiveRestdate()).isEqualTo("매주 월요일");
        assertThat(cultureItem.getEffectiveParking()).isEqualTo("지하주차장 무료");
        assertThat(cultureItem.getEffectiveInfocenter()).isEqualTo("042-870-1200");
        assertThat(cultureItem.getEffectiveStrollerAccess()).isEqualTo("유모차 대여 가능");
        assertThat(cultureItem.getEffectivePetFriendly()).isEqualTo("반려동물 동반 불가");

        // 2. 음식점 (39)
        String foodJson = """
                {
                  "response": {
                    "body": {
                      "items": {
                        "item": [
                          {
                            "contentid": "3094599",
                            "opentimefood": "10:00 ~ 22:00",
                            "restdatefood": "연중무휴",
                            "parkingfood": "전용 주차장 20대",
                            "infocenterfood": "02-123-4567"
                          }
                        ]
                      }
                    }
                  }
                }
                """;

        TourApiIntroResponse foodRes = objectMapper.readValue(foodJson, TourApiIntroResponse.class);
        TourApiIntroResponse.IntroItem foodItem = foodRes.response().body().items().item().get(0);

        assertThat(foodItem.getEffectiveUsetime()).isEqualTo("10:00 ~ 22:00");
        assertThat(foodItem.getEffectiveRestdate()).isEqualTo("연중무휴");
        assertThat(foodItem.getEffectiveParking()).isEqualTo("전용 주차장 20대");
        assertThat(foodItem.getEffectiveInfocenter()).isEqualTo("02-123-4567");
    }
}