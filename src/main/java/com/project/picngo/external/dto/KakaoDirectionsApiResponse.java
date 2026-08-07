package com.project.picngo.external.dto;

import java.util.List;

public record KakaoDirectionsApiResponse(
        List<Route> routes
) {
    public record Route(
            Integer result_code,
            String result_msg,
            Summary summary
    ) {}

    public record Summary(
            Integer distance,
            Integer duration
    ) {}
}
