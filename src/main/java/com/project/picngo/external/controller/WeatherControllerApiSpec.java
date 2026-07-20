package com.project.picngo.external.controller;

import com.project.picngo.spot.dto.CurrentWeatherResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "날씨 (Weather)", description = "현재 위치 기반 날씨 조회 API")
public interface WeatherControllerApiSpec {

    @Operation(summary = "현재 위치 날씨 조회",
            description = "좌표 기준 현재 날씨·기온·미세먼지·오존·다음 골든아워를 한글로 반환합니다. 외부 API 실패 시 해당 필드는 null.")
    ResponseEntity<CurrentWeatherResponse> getCurrentWeather(
            @Parameter(description = "위도", example = "37.5665") @RequestParam Double lat,
            @Parameter(description = "경도", example = "126.9780") @RequestParam Double lng
    );
}
