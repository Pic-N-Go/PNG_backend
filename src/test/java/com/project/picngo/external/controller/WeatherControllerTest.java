package com.project.picngo.external.controller;

import com.google.firebase.messaging.FirebaseMessaging;
import com.project.picngo.config.FcmConfig;
import com.project.picngo.external.KakaoRegionClient;
import com.project.picngo.external.service.WeatherCacheService;
import com.project.picngo.external.service.WeatherService;
import com.project.picngo.notification.service.FcmService;
import com.project.picngo.spot.dto.CurrentWeatherResponse;
import com.project.picngo.spot.dto.CurrentWeatherResponse.AirGrade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WeatherControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean WeatherService weatherService;
    @MockitoBean KakaoRegionClient kakaoRegionClient;
    @MockitoBean WeatherCacheService weatherCacheService;

    // Firebase mocks for context loading
    @MockitoBean FcmConfig fcmConfig;
    @MockitoBean FirebaseMessaging firebaseMessaging;
    @MockitoBean FcmService fcmService;

    @Test
    @WithMockUser
    @DisplayName("좌표로 현재 날씨를 200으로 반환한다")
    void returnsCurrentWeather() throws Exception {
        when(weatherService.getCurrentWeather(any(), any())).thenReturn(
                new CurrentWeatherResponse("서울", "맑음", 28.0,
                        new AirGrade("좋음", 25.0), new AirGrade("보통", 0.03), "19:12"));

        mockMvc.perform(get("/weather/current").param("lat", "37.56").param("lng", "126.97"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.region").value("서울"))
                .andExpect(jsonPath("$.weatherStatus").value("맑음"))
                .andExpect(jsonPath("$.fineDust.grade").value("좋음"))
                .andExpect(jsonPath("$.goldenHour").value("19:12"));
    }

    @Test
    @WithMockUser
    @DisplayName("lat/lng 누락 시 400")
    void missingParamsReturns400() throws Exception {
        mockMvc.perform(get("/weather/current").param("lat", "37.56"))
                .andExpect(status().isBadRequest());
    }
}
