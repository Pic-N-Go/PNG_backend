package com.project.picngo.external.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoAddressApiResponse(
        List<Document> documents
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Document(
            @JsonProperty("road_address")
            Address roadAddress,
            Address address
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Address(
            @JsonProperty("address_name")
            String addressName
    ) {
    }
}
