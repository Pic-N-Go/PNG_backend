package com.project.picngo.external.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LdongCodeApiResponse(Response response) {

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
            @JsonProperty("item") List<LdongItem> item
    ) {
        @JsonCreator
        public static Items fromObject(@JsonProperty("item") List<LdongItem> item) {
            return new Items(item != null ? item : Collections.emptyList());
        }

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public static Items fromString(String value) {
            return new Items(Collections.emptyList());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LdongItem(
            String rnum,
            String lDongRegnCd,
            String lDongRegnNm
    ) {}
}
