package com.project.picngo.external.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TourApiImageResponse(Response response) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(Body body) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Body(Items items) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Items(
            @JsonProperty("item") List<ImageItem> item
    ) {
        @JsonCreator
        public static Items fromObject(@JsonProperty("item") List<ImageItem> item) {
            return new Items(item != null ? item : Collections.emptyList());
        }

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public static Items fromString(String value) {
            return new Items(Collections.emptyList());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ImageItem(
            String contentid,
            String originimgurl,
            String smallimageurl,
            String imgname,
            String serialnum
    ) {}
}
