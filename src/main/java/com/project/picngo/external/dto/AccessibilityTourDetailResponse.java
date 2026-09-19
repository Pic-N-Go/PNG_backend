package com.project.picngo.external.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AccessibilityTourDetailResponse(Response response) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(
            Header header,
            Body body
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Header(
            String resultCode,
            String resultMsg
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Body(
            Items items,
            int totalCount
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Items(@JsonProperty("item") List<Item> item) {

        @JsonCreator
        public static Items fromObject(@JsonProperty("item") List<Item> item) {
            return new Items(item != null ? item : Collections.emptyList());
        }

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public static Items fromString(String ignored) {
            return new Items(Collections.emptyList());
        }

        public List<Item> safeItems() {
            return item != null ? item : Collections.emptyList();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            String contentid,
            String parking,
            String publictransport,
            String route,
            String ticketoffice,
            String promotion,
            String wheelchair,
            String exit,
            String elevator,
            String restroom,
            String auditorium,
            String room,
            String handicapetc,
            String braileblock,
            String helpdog,
            String guidehuman,
            String audioguide,
            String bigprint,
            String brailepromotion,
            String guidesystem,
            String blindhandicapetc,
            String signguide,
            String videoguide,
            String hearingroom,
            String hearinghandicapetc,
            String stroller,
            String lactationroom,
            String babysparechair,
            String infantsfamilyetc
    ) {
    }
}
