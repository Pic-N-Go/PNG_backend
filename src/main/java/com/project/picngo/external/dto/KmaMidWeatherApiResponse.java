package com.project.picngo.external.dto;

import java.util.List;

public record KmaMidWeatherApiResponse(Response response) {
    public record Response(Header header, Body body) {}
    public record Header(String resultCode, String resultMsg) {}
    public record Body(String dataType, Items items, int pageNo, int numOfRows, int totalCount) {}
    public record Items(List<Item> item) {}
    
    public record Item(
            String regId,
            Integer rnSt3Am, Integer rnSt3Pm, Integer rnSt4Am, Integer rnSt4Pm, Integer rnSt5Am, Integer rnSt5Pm, Integer rnSt6Am, Integer rnSt6Pm, Integer rnSt7Am, Integer rnSt7Pm,
            Integer rnSt8, Integer rnSt9, Integer rnSt10,
            String wf3Am, String wf3Pm, String wf4Am, String wf4Pm, String wf5Am, String wf5Pm, String wf6Am, String wf6Pm, String wf7Am, String wf7Pm,
            String wf8, String wf9, String wf10
    ) {}
}
