package com.project.picngo.external.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TourApiIntroResponse(Response response) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(Body body) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Body(Items items) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Items(List<IntroItem> item) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record IntroItem(
            String contentid,
            String parking,
            String usetime,
            String restdate,
            String infocenter,
            String chkbabycarriage,
            String chkpet,
            String chkhandicap
    ) {}
}
