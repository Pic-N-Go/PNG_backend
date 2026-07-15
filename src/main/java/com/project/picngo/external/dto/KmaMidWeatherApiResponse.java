package com.project.picngo.external.dto;

import java.util.List;

public record KmaMidWeatherApiResponse(Response response) {
    public record Response(Header header, Body body) {}
    public record Header(String resultCode, String resultMsg) {}
    public record Body(String dataType, Items items, int pageNo, int numOfRows, int totalCount) {}
    public record Items(List<Item> item) {}
    
    public record Item(
            String regId,
            int rnSt3Am, int rnSt3Pm, int rnSt4Am, int rnSt4Pm, int rnSt5Am, int rnSt5Pm, int rnSt6Am, int rnSt6Pm, int rnSt7Am, int rnSt7Pm,
            int rnSt8, int rnSt9, int rnSt10,
            String wf3Am, String wf3Pm, String wf4Am, String wf4Pm, String wf5Am, String wf5Pm, String wf6Am, String wf6Pm, String wf7Am, String wf7Pm,
            String wf8, String wf9, String wf10
    ) {}
}
