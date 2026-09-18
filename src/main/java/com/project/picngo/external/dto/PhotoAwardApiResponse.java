package com.project.picngo.external.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PhotoAwardApiResponse(Response response) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(Header header, Body body) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Header(String resultCode, String resultMsg) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Body(
            Items items,
            int numOfRows,
            int pageNo,
            int totalCount
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Items(
            @JsonProperty("item") List<PhotoAwardItem> item
    ) {
        @JsonCreator
        public static Items fromObject(@JsonProperty("item") List<PhotoAwardItem> item) {
            return new Items(item != null ? item : Collections.emptyList());
        }

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public static Items fromString(String value) {
            return new Items(Collections.emptyList());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PhotoAwardItem(
            String contentId,
            String koTitle,
            String enTitle,
            String lDongRegnCd,
            String koFilmst,
            String enFilmst,
            String filmDay,
            String koCmanNm,
            String enCmanNm,
            String koWnprzDiz,
            String enWnprzDiz,
            @JsonAlias({"koKeyword", "koKeyWord"}) String koKeyWord,
            @JsonAlias({"enKeyword", "enKeyWord"}) String enKeyWord,
            String orgImage,
            String thumbImage,
            String cpyrhtDivCd,
            String regDt,
            String mdfcnDt,
            String showflag
    ) {}
}
