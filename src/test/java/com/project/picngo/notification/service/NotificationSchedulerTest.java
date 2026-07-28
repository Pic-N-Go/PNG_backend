package com.project.picngo.notification.service;

import com.project.picngo.external.dto.WeatherForecastResponse;
import com.project.picngo.external.service.WeatherCacheService;
import com.project.picngo.notification.domain.NotificationSetting;
import com.project.picngo.notification.repository.NotificationSettingRepository;
import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.repository.SpotRepository;
import com.project.picngo.wishlist.domain.Wishlist;
import com.project.picngo.wishlist.domain.enums.TimeCondition;
import com.project.picngo.wishlist.domain.enums.WeatherCondition;
import com.project.picngo.wishlist.repository.WishlistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NotificationSchedulerTest {

    @Autowired
    private NotificationScheduler notificationScheduler;

    @MockitoBean
    private WeatherCacheService weatherCacheService;

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private com.google.firebase.messaging.FirebaseMessaging firebaseMessaging;

    // 활성 유저 조회(Redis 의존)를 목으로 대체하여 테스트를 결정적으로 만든다
    @MockitoBean
    private NotificationCacheService notificationCacheService;

    @Autowired
    private SpotRepository spotRepository;

    @Autowired
    private WishlistRepository wishlistRepository;

    @Autowired
    private NotificationSettingRepository notificationSettingRepository;

    private Spot savedSpot;
    // data.sql 시드(1~101번)와 겹치지 않는 userId 사용 (notification_setting UNIQUE 충돌 방지)
    private Long userId = 999_999L;

    @BeforeEach
    void setUp() {
        // Mock 데이터 세팅 (DB Insert)
        NotificationSetting setting = NotificationSetting.builder()
                .userId(userId)
                .build();
        setting.updateFcmToken("test-fcm-token");
        notificationSettingRepository.save(setting);

        Spot spot = Spot.builder()
                .name("경복궁 야간개장")
                .address("서울특별시 종로구 사직로 161")
                .latitude(37.5796)
                .longitude(126.9770)
                .category(com.project.picngo.spot.domain.SpotCategory.NIGHT_VIEW)
                .source(com.project.picngo.spot.domain.enums.SpotSource.USER)
                .build();
        savedSpot = spotRepository.save(spot);

        // 스케줄러의 활성 위시리스트 유저 조회가 테스트 유저를 반환하도록 스텁
        when(notificationCacheService.getActiveUserIds("wishlist")).thenReturn(Set.of(userId));
    }

    @Test
    @DisplayName("[통합 시나리오] 유저가 '맑음'을 조건으로 걸었을 때, 기상청(Mock)이 '맑음'을 반환하면 알림 발송됨")
    void testWeatherMatchSuccess() {
        // Given: 유저가 1일 뒤 '맑음'을 알림 조건으로 설정함
        Wishlist wishlist = Wishlist.builder()
                .userId(userId)
                .spotId(savedSpot.getId())
                .build();
        wishlist.updateSettings(
                "테스트 메모",
                Set.of(WeatherCondition.CLEAR),
                Set.of(TimeCondition.AFTERNOON),
                com.project.picngo.wishlist.domain.enums.AirQualityCondition.NONE,
                1, // 1일 뒤 (내일)
                true
        );
        wishlistRepository.save(wishlist);

        // 오늘 날짜 및 타겟 날짜 계산
        String todayStr = LocalDate.now(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String targetDateStr = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(1).format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        // 가짜(Mock) 기상청 데이터 설정: 1일 뒤 오후 2시(1400)에 맑음(CLEAR) 반환
        List<WeatherForecastResponse> mockForecast = List.of(
                new WeatherForecastResponse(targetDateStr, "1400", "CLEAR", 10.0)
        );
        when(weatherCacheService.getCached7DayForecast(any(), any(), eq(todayStr)))
                .thenReturn(mockForecast);

        // When: 오후 스케줄러 실행
        notificationScheduler.scheduleAfternoonNotification();

        // Then: NotificationService의 sendPushNotification이 정확히 1번 호출되었는지 검증
        //  (시간대별 메시지 구분 + dedupeKey 도입으로 제목/시그니처가 변경됨)
        verify(notificationService, times(1)).sendPushNotification(
                eq(userId),
                eq("WEATHER_MATCH"),
                eq("☁️ 오후 날씨 조건 알림"),
                contains("경복궁 야간개장"),
                anyString(),               // deepLink
                eq(savedSpot.getId()),     // spotId
                anyString()                // dedupeKey
        );
    }

    @Test
    @DisplayName("[통합 시나리오] 유저가 '맑음'을 조건으로 걸었는데, 기상청(Mock)이 '비'를 반환하면 알림 발송안됨")
    void testWeatherMatchFail() {
        // Given
        Wishlist wishlist = Wishlist.builder()
                .userId(userId)
                .spotId(savedSpot.getId())
                .build();
        wishlist.updateSettings(
                "테스트 메모",
                Set.of(WeatherCondition.CLEAR),
                Set.of(TimeCondition.AFTERNOON),
                com.project.picngo.wishlist.domain.enums.AirQualityCondition.NONE,
                1,
                true
        );
        wishlistRepository.save(wishlist);

        String todayStr = LocalDate.now(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String targetDateStr = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(1).format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        // 가짜 데이터: 비(RAINY)가 옴
        List<WeatherForecastResponse> mockForecast = List.of(
                new WeatherForecastResponse(targetDateStr, "1400", "RAINY", 10.0)
        );
        when(weatherCacheService.getCached7DayForecast(any(), any(), eq(todayStr)))
                .thenReturn(mockForecast);

        // When
        notificationScheduler.scheduleAfternoonNotification();

        // Then: 날씨가 맞지 않으므로 sendPushNotification이 한 번도 호출되지 않아야 함 (7-arg 시그니처 기준)
        verify(notificationService, never()).sendPushNotification(
                any(), any(), any(), any(), any(), any(), any());
    }
}
