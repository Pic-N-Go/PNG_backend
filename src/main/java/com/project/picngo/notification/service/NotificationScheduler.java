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

        String today = LocalDate.now(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        // TODO: 향후 Spot 연동 시 유저들의 위시리스트 좌표들을 모아 그룹화(Set)하여 기상청 API를 좌표별로 한 번씩만 캐싱 조회하고, 알림도 묶어서 발송(Batch)하도록 성능 고도화 필요
        Double lat = 37.5665;
        Double lng = 126.9780;
        List<WeatherForecastResponse> forecasts = List.of();
        try {
            forecasts = weatherClient.getForecast(lat, lng, today);
        } catch (Exception e) {
            log.error("스케줄러 기상청 API 초기 조회 실패", e);
        }

        for (NotificationSetting setting : activeSettings) {
            Long userId = setting.getUserId();
            List<WishlistItem> userItems = wishlistItemRepository.findAllByWishlist_UserIdAndIsActiveTrue(userId);
            
            for (WishlistItem item : userItems) {
                try {
                    // TODO: 단순히 첫 번째 예보(forecasts.get(0))만 확인하지 말고, 낮 시간대(오전 9시~오후 6시) 등 전체 예보를 순회하며 매칭되는 조건이 있는지 확인하는 로직으로 고도화 필요
                    if (!forecasts.isEmpty()) {
                        WeatherForecastResponse forecast = forecasts.get(0);
                        String apiWeather = forecast.weatherStatus();
                        WeatherCondition userWeather = item.getWeatherCondition();
                        
                        boolean weatherMatch = userWeather == WeatherCondition.NONE || apiWeather.equals(userWeather.name());
                        // TODO TimeCondition (SUNRISE/SUNSET)은 현재 KMA에선 판단하기 어렵지만, 골든아워 API와 연동 로직 추가 -> Spot 도메인 연동 후 구현 예정
                        
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
