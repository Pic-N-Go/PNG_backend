package com.project.picngo.external.controller;

import com.project.picngo.external.service.WeatherService;
import com.project.picngo.spot.dto.CurrentWeatherResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class WeatherController implements WeatherControllerApiSpec {

    private final WeatherService weatherService;

    @Override
    @GetMapping("/weather/current")
    public ResponseEntity<CurrentWeatherResponse> getCurrentWeather(
            @RequestParam Double lat,
            @RequestParam Double lng) {
        return ResponseEntity.ok(weatherService.getCurrentWeather(lat, lng));
    }
}
