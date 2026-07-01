package com.project.picngo.notification.service;

import com.project.picngo.external.WeatherClient;
import com.project.picngo.external.dto.WeatherForecastResponse;
import com.project.picngo.notification.domain.NotificationSetting;
import com.project.picngo.notification.repository.NotificationSettingRepository;
import com.project.picngo.wishlist.domain.WishlistItem;
import com.project.picngo.wishlist.domain.enums.TimeCondition;
import com.project.picngo.wishlist.domain.enums.WeatherCondition;
import com.project.picngo.wishlist.repository.WishlistItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationScheduler {

    private final NotificationSettingRepository notificationSettingRepository;
    private final NotificationService notificationService;
    private final WishlistItemRepository wishlistItemRepository;
    private final WeatherClient weatherClient;

    // 매일 아침 7시에 실행
    @Scheduled(cron = "0 0 7 * * *")
    public void scheduleDailyPushNotifications() {
        log.info("매일 아침 7시 알림 스케줄러 실행 시작...");

        List<NotificationSetting> activeSettings = notificationSettingRepository.findActiveSettingsWithToken().stream()
                .filter(setting -> !isDndActive(setting))
                .toList();

        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        for (NotificationSetting setting : activeSettings) {
            Long userId = setting.getUserId();
            List<WishlistItem> userItems = wishlistItemRepository.findAllByWishlist_UserIdAndIsActiveTrue(userId);
            
            for (WishlistItem item : userItems) {
                // 임시 하드코딩 위경도 (Spot 도메인 연동 전까지 서울 좌표 사용)
                Double lat = 37.5665;
                Double lng = 126.9780;

                try {
                    List<WeatherForecastResponse> forecasts = weatherClient.getForecast(lat, lng, today);
                    
                    // 조건 확인 (간단하게 첫 번째 예보와 유저의 weatherCondition이 맞는지 확인)
                    if (!forecasts.isEmpty()) {
                        WeatherForecastResponse forecast = forecasts.get(0);
                        String apiWeather = forecast.weatherStatus();
                        WeatherCondition userWeather = item.getWeatherCondition();
                        
                        boolean weatherMatch = userWeather == WeatherCondition.NONE || apiWeather.equals(userWeather.name());
                        // TimeCondition (SUNRISE/SUNSET)은 현재 KMA에선 판단하기 어렵지만, 골든아워 API와 연동 로직 추가 가능
                        
                        if (weatherMatch) {
                            String title = "오늘의 추천 여행지 알림 ☀️";
                            String body = "회원님의 위시리스트 날씨 조건과 완벽하게 일치하는 장소가 있습니다!";
                            notificationService.sendPushNotification(userId, "WEATHER_MATCH", title, body, "/wishlist/" + item.getWishlist().getId());
                            log.info("유저 {} 에게 스케줄러 푸시 알림 발송 완료 (SpotId: {})", userId, item.getSpotId());
                            break; // 하루 한 번만 발송하도록 제어
                        }
                    }
                } catch (Exception e) {
                    log.error("유저 {} 의 위시리스트 체크 중 오류 발생", userId, e);
                }
            }
        }
        
        log.info("매일 아침 7시 알림 스케줄러 실행 종료.");
    }

    private boolean isDndActive(NotificationSetting setting) {
        if (setting.getDndStartTime() == null || setting.getDndEndTime() == null) {
            return false;
        }
        
        LocalTime now = LocalTime.now(ZoneId.of("Asia/Seoul"));
        LocalTime start = setting.getDndStartTime();
        LocalTime end = setting.getDndEndTime();

        if (start.isBefore(end)) {
            // 예: 10:00 ~ 18:00
            return !now.isBefore(start) && now.isBefore(end);
        } else {
            // 예: 22:00 ~ 07:00 (자정 넘어가는 경우)
            return !now.isBefore(start) || now.isBefore(end);
        }
    }
}
