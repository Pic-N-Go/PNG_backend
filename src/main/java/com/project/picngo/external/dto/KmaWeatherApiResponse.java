package com.project.picngo.external.dto;

import java.util.List;

public record KmaWeatherApiResponse(
        Response response
) {
    public record Response(
            Header header,
            Body body
    ) {}

    public record Header(
            String resultCode,
            String resultMsg
    ) {}

    public record Body(
            Items items
    ) {}

    public record Items(
            List<Item> item
    ) {}

    public record Item(
            String baseDate,
            String baseTime,
            String category,
            String fcstDate,
            String fcstTime,
            String fcstValue,
            Integer nx,
            Integer ny
    ) {}
}
