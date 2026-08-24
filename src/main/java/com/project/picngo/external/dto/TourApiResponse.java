package com.project.picngo.external.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TourApiResponse(Response response) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(Header header, Body body) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Header(String resultCode, String resultMsg) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Body(Items items, int numOfRows, int pageNo, int totalCount) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Items(List<Item> item) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            String contentid,
            String contenttypeid,
            String title,
            String addr1,
            String addr2,
            String zipcode,
            String mapx,
            String mapy,
            String cat1,
            String cat2,
            String cat3,
            String lclsSystm1,
            String lclsSystm2,
            String lclsSystm3,
            String lDongRegnCd,
            String lDongSignguCd,
            String firstimage,
            String firstimage2,
            String overview,
            String usetime,
            String restdate,
            String infocenter,
            String parking,
            String chkbabycarriage,
            String chkpet,
            String chkhandichief,
            String tel,
            String homepage,
            String eventstartdate,
            String eventenddate,
            String progresstype
    ) {
        /**
         * 구버전(cat3) 또는 신버전(lclsSystm3) 중 존재하는 소분류 카테고리 코드를 반환한다.
         */
        public String getEffectiveCategoryCode() {
            if (lclsSystm3 != null && !lclsSystm3.isBlank()) {
                return lclsSystm3.trim();
            }
            if (cat3 != null && !cat3.isBlank()) {
                return cat3.trim();
            }
            return null;
        }

        public Integer getContentTypeIdOrNull() {
            if (contenttypeid == null || contenttypeid.isBlank()) return null;
            try {
                return Integer.parseInt(contenttypeid.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }
}
