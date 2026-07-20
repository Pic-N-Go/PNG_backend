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
            String firstimage,
            String firstimage2,
            String overview,
            String usetime,
            String restdate,
            String infocenter,
            String parking,
            String chkbabycarriage,
            String chkpet,
            String chkhandichief
    ) {}
}
