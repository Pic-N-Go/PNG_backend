package com.project.picngo.external.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TourApiImageResponse(Response response) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(Body body) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Body(Items items) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Items(List<ImageItem> item) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ImageItem(
            String contentid,
            String originimgurl,
            String smallimageurl,
            String imgname,
            String serialnum
    ) {}
}
