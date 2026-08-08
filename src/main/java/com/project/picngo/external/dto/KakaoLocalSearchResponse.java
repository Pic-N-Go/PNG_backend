package com.project.picngo.external.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record KakaoLocalSearchResponse(
        List<PlaceDocument> documents
) {
    public record PlaceDocument(
            @JsonProperty("place_name") String placeName,
            @JsonProperty("distance") String distance,
            @JsonProperty("x") String x, // longitude
            @JsonProperty("y") String y  // latitude
    ) {}
}
