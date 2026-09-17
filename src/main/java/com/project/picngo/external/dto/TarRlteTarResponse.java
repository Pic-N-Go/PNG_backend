package com.project.picngo.external.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TarRlteTarResponse(Response response) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(Header header, Body body) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Header(String resultCode, String resultMsg) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Body(Items items, int numOfRows, int pageNo, int totalCount) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Items(
            @JsonProperty("item") List<Item> item
    ) {
        @JsonCreator
        public static Items fromObject(@JsonProperty("item") List<Item> item) {
            return new Items(item != null ? item : Collections.emptyList());
        }

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public static Items fromString(String value) {
            return new Items(Collections.emptyList());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            String baseYm,
            String tAtsCd,
            String tAtsNm,
            String areaCd,
            String areaNm,
            String signguCd,
            String signguNm,
            String rlteTatsCd,
            String rlteTatsNm,
            String rlteRegnCd,
            String rlteRegnNm,
            String rlteSignguCd,
            String rlteSignguNm,
            String rlteCtgryLclsNm,
            String rlteCtgryMclsNm,
            String rlteCtgrySclsNm,
            String rlteRank
    ) {
        public int getRankOrZero() {
            if (rlteRank == null || rlteRank.isBlank()) {
                return 0;
            }
            try {
                return Integer.parseInt(rlteRank.trim());
            } catch (NumberFormatException e) {
                return 0;
            }
        }
    }
}
