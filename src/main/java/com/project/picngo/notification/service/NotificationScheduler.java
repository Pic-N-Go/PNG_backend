package com.project.picngo.notification.service;

import com.project.picngo.external.WeatherClient;
import com.project.picngo.external.dto.WeatherForecastResponse;
import com.project.picngo.external.dto.GoldenHourResponse;
import com.project.picngo.external.service.WeatherCacheService;
import com.project.picngo.notification.domain.NotificationSetting;
import com.project.picngo.notification.repository.NotificationSettingRepository;
import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.repository.SpotRepository;
import com.project.picngo.wishlist.domain.Wishlist;
import com.project.picngo.wishlist.domain.enums.TimeCondition;
import com.project.picngo.wishlist.domain.enums.WeatherCondition;
import com.project.picngo.wishlist.repository.WishlistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationScheduler {

    private final NotificationSettingRepository notificationSettingRepository;
    private final WishlistRepository wishlistRepository;
    private final SpotRepository spotRepository;
    private final WeatherCacheService weatherCacheService;
    private final NotificationService notificationService;

    // [A. 고정 시간대 스케줄러]
    // 해당 시간에 스케줄러가 돌아가며, 조건이 맞는 스팟들에 대해 알림 발송

    // 22시에 새벽 알림 발송
    @Scheduled(cron = "0 0 22 * * *")
    public void scheduleDawnNotification() {
        log.info("새벽(DAWN) 알림 스케줄러 실행...");
        processFixedTimeNotification(TimeCondition.DAWN);
    }

    // 4시에 오전 알림 발송
    @Scheduled(cron = "0 0 4 * * *")
    public void scheduleMorningNotification() {
        log.info("오전(MORNING) 알림 스케줄러 실행...");
        processFixedTimeNotification(TimeCondition.MORNING);
    }

    // 10시에 오후 알림 발송
    @Scheduled(cron = "0 0 10 * * *")
    public void scheduleAfternoonNotification() {
        log.info("오후(AFTERNOON) 알림 스케줄러 실행...");
        processFixedTimeNotification(TimeCondition.AFTERNOON);
    }

    // 16시에 야간 알림 발송
    @Scheduled(cron = "0 0 16 * * *")
    public void scheduleNightNotification() {
        log.info("야간(NIGHT) 알림 스케줄러 실행...");
        processFixedTimeNotification(TimeCondition.NIGHT);
    }

    // [B. 동적 시간대(골든아워) 스케줄러]

    // 일출 스케줄러 (04:00 ~ 08:00 사이 매 10분마다 실행)
    @Scheduled(cron = "0 0/10 4-8 * * *")
    public void scheduleSunriseNotification() {
        log.info("일출(SUNRISE) 임박 알림 스케줄러 실행...");
        processGoldenHourNotification(TimeCondition.SUNRISE);
    }

    // 일몰 스케줄러 (16:00 ~ 20:00 사이 매 10분마다 실행)
    @Scheduled(cron = "0 0/10 16-20 * * *")
    public void scheduleSunsetNotification() {
        log.info("일몰(SUNSET) 임박 알림 스케줄러 실행...");
        processGoldenHourNotification(TimeCondition.SUNSET);
    }

    // --- 공통 로직 ---

    // 고정된 시간대 알림 처리 (위시리스트 날씨 매칭)
    private void processFixedTimeNotification(TimeCondition timeCondition) {
        List<NotificationSetting> activeSettings = getActiveWishlistSettings();

        for (NotificationSetting setting : activeSettings) {
            Long userId = setting.getUserId();
            List<Wishlist> userWishlists = wishlistRepository.findAllByUserIdAndIsActiveTrue(userId);
            
            for (Wishlist wishlist : userWishlists) {
                if (wishlist.getTimeConditions().contains(timeCondition)) {
                    log.info("유저 {} 의 스팟 {} 에 대해 {} 알림 조건 충족 확인 중...", userId, wishlist.getSpotId(), timeCondition);
                    checkWeatherAndNotify(userId, wishlist);
                }
            }
        }
    }

    // 매일 변하는 자연 현상(일출/일몰)의 타이밍을 실시간으로 계산하는 타이머 (골든아워)
    private void processGoldenHourNotification(TimeCondition timeCondition) {
        List<NotificationSetting> activeSettings = getActiveGoldenHourSettings(); // 골든아워 알림 수신 동의 유저들 목록 조회

        for (NotificationSetting setting : activeSettings) {
            Long userId = setting.getUserId();
            List<Wishlist> userWishlists = wishlistRepository.findAllByUserIdAndIsActiveTrue(userId);

            for (Wishlist wishlist : userWishlists) {
                if (wishlist.getTimeConditions().contains(timeCondition)) {
                    try {
                        Optional<Spot> spotOpt = spotRepository.findById(wishlist.getSpotId());
                        if (spotOpt.isEmpty()) continue;
                        
                        Spot spot = spotOpt.get();
                        Double lat = spot.getLatitude();
                        Double lng = spot.getLongitude();
                        
                        int dDay = wishlist.getAlertTimingDays() != null ? wishlist.getAlertTimingDays() : 0;
                        String targetDate = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(dDay).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                        
                        GoldenHourResponse gh = weatherCacheService.getCachedGoldenHour(lat, lng, targetDate);
                        
                        String targetUtcTimeStr = timeCondition == TimeCondition.SUNRISE ? gh.sunriseTime() : gh.sunsetTime();
                        if (targetUtcTimeStr != null && !targetUtcTimeStr.isEmpty()) {
                            // ISO 8601 parsing e.g., 2015-05-21T05:05:35+00:00
                            java.time.ZonedDateTime targetUtc = java.time.ZonedDateTime.parse(targetUtcTimeStr, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                            java.time.ZonedDateTime targetKst = targetUtc.withZoneSameInstant(ZoneId.of("Asia/Seoul"));
                            
                            java.time.ZonedDateTime now = java.time.ZonedDateTime.now(ZoneId.of("Asia/Seoul"));
                            
                            // 스케줄러가 일치해야 할 '오늘의 알림 타겟 시간'을 D-Day의 대상 시간과 동일한 시간으로 간주
                            java.time.ZonedDateTime alertTime = now.withHour(targetKst.getHour()).withMinute(targetKst.getMinute()).withSecond(0).withNano(0);
                            
                            // 현재 시간이 알림 발송 시간(alertTime)의 반경 10분 이내인지 확인 (스케줄러가 10분마다 도므로)
                            long diffMinutes = java.time.Duration.between(now, alertTime).toMinutes();
                            if (Math.abs(diffMinutes) <= 5) {
                                log.info("유저 {} 의 스팟 {} 에 대해 {} 골든아워 임박 알림 발송 조건 충족! (D-{})", userId, spot.getId(), timeCondition, dDay);
                                String dayStr = dDay == 0 ? "오늘" : dDay + "일 뒤";
                                String title = "🌅 골든아워 알림";
                                String content = String.format("%s %s %s 시간은 %02d시 %02d분 입니다.", dayStr, spot.getName(), timeCondition == TimeCondition.SUNRISE ? "일출" : "일몰", targetKst.getHour(), targetKst.getMinute());
                                notificationService.sendPushNotification(userId, "GOLDEN_HOUR", title, content, "/wishlist/" + spot.getId());
                            }
                        }

                    } catch (Exception e) {
                        log.error("골든아워 확인 중 오류 발생 (유저: {}, 스팟: {})", userId, wishlist.getSpotId(), e);
                    }
                }
            }
        }
    }

    // 날씨 및 미세먼지 조건 확인 후 알림 발송
    private void checkWeatherAndNotify(Long userId, Wishlist wishlist) {
        try {
            Optional<Spot> spotOpt = spotRepository.findById(wishlist.getSpotId());
            if (spotOpt.isEmpty()) return;
            
            Spot spot = spotOpt.get();
            Double lat = spot.getLatitude();
            Double lng = spot.getLongitude();
            
            int dDay = wishlist.getAlertTimingDays() != null ? wishlist.getAlertTimingDays() : 0;
            String todayStr = LocalDate.now(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String targetDateStr = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(dDay).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            
            // 캐싱된 7일 예보 조회
            List<WeatherForecastResponse> combinedForecast = weatherCacheService.getCached7DayForecast(lat, lng, todayStr);
            
            Set<WeatherCondition> userConditions = wishlist.getWeatherConditions();
            if (userConditions == null || userConditions.isEmpty()) {
                return;
            }

            // 다중 조건 매칭 로직 (타겟 날짜의 10시, 14시, 18시만 확인)
            boolean isMatched = false;
            if (userConditions.contains(WeatherCondition.NONE)) {
                isMatched = true;
            } else {
                for (WeatherForecastResponse forecast : combinedForecast) {
                    if (forecast.date().equals(targetDateStr) && 
                       (forecast.time().equals("1000") || forecast.time().equals("1400") || forecast.time().equals("1800"))) {
                        
                        try {
                            WeatherCondition apiWeather = WeatherCondition.valueOf(forecast.weatherStatus());
                            if (userConditions.contains(apiWeather)) {
                                isMatched = true;
                                break;
                            }
                        } catch (IllegalArgumentException e) {
                            log.warn("알 수 없는 기상청 날씨 상태 수신 (스킵 처리): {}", forecast.weatherStatus());
                        }
                    }
                }
            }

            if (isMatched) {
                // 미세먼지 조건 필터링
                boolean isAirQualityMatched = true; 
                com.project.picngo.wishlist.domain.enums.AirQualityCondition aqCondition = wishlist.getAirQualityCondition();
                
                if (aqCondition != null && aqCondition != com.project.picngo.wishlist.domain.enums.AirQualityCondition.NONE) {
                    try {
                        String address = spot.getAddress();
                        String sidoName = "서울";
                        if (address != null && address.length() >= 2) {
                            if (address.startsWith("충청북도")) sidoName = "충북";
                            else if (address.startsWith("충청남도")) sidoName = "충남";
                            else if (address.startsWith("전라북도")) sidoName = "전북";
                            else if (address.startsWith("전라남도")) sidoName = "전남";
                            else if (address.startsWith("경상북도")) sidoName = "경북";
                            else if (address.startsWith("경상남도")) sidoName = "경남";
                            else sidoName = address.substring(0, 2);
                        }
                        
                        com.project.picngo.external.dto.AirQualityResponse.Item aqItem = weatherCacheService.getCachedAirQuality(sidoName);
                        
                        if (aqItem != null && aqItem.pm10Grade() != null) {
                            int pm10Grade = Integer.parseInt(aqItem.pm10Grade());
                            if (aqCondition == com.project.picngo.wishlist.domain.enums.AirQualityCondition.GOOD && pm10Grade > 1) {
                                isAirQualityMatched = false;
                            } else if (aqCondition == com.project.picngo.wishlist.domain.enums.AirQualityCondition.NORMAL_OR_BETTER && pm10Grade > 2) {
                                isAirQualityMatched = false;
                            }
                        }
                    } catch (Exception e) {
                        log.warn("미세먼지 확인 중 오류 발생, 알림 발송은 진행합니다.", e);
                    }
                }

                if (isAirQualityMatched) {
                    log.info("유저 {} 의 스팟 {} 에 대한 날씨 및 미세먼지 조건이 일치합니다! (D-{})", userId, spot.getId(), dDay);
                    String dayStr = dDay == 0 ? "오늘" : dDay + "일 뒤";
                    String title = "☁️ 날씨 조건 매칭 알림";
                    String content = String.format("%s %s에 설정하신 날씨 조건이 충족될 예정입니다!", dayStr, spot.getName());
                    notificationService.sendPushNotification(userId, "WEATHER_MATCH", title, content, "/wishlist/" + spot.getId());
                }
            }

        } catch (Exception e) {
            log.error("유저 {} 의 위시리스트 체크 중 오류 발생", userId, e);
        }
    }

    private List<NotificationSetting> getActiveWishlistSettings() {
        return notificationSettingRepository.findActiveWishlistSettingsWithToken().stream()
                .filter(setting -> !isDndActive(setting))
                .toList();
    }

    private List<NotificationSetting> getActiveGoldenHourSettings() {
        return notificationSettingRepository.findActiveGoldenHourSettingsWithToken().stream()
                .filter(setting -> !isDndActive(setting))
                .toList();
    }

    // 방해 금지 시간 (DND) 활성 여부 확인 및 활성 설정 필터링
    private boolean isDndActive(NotificationSetting setting) {
        if (setting.getDndStartTime() == null || setting.getDndEndTime() == null) {
            return false;
        }
        
        LocalTime now = LocalTime.now(ZoneId.of("Asia/Seoul"));
        LocalTime start = setting.getDndStartTime();
        LocalTime end = setting.getDndEndTime();

        if (start.isBefore(end)) {
            return !now.isBefore(start) && now.isBefore(end);
        } else {
            return !now.isBefore(start) || now.isBefore(end);
        }
    }

    public void triggerAllSchedulersManually() {
        log.info("테스트용 수동 스케줄러 강제 실행 시작...");
        processFixedTimeNotification(TimeCondition.MORNING);
        processFixedTimeNotification(TimeCondition.AFTERNOON);
        processGoldenHourNotification(TimeCondition.SUNRISE);
        processGoldenHourNotification(TimeCondition.SUNSET);
        log.info("테스트용 수동 스케줄러 강제 실행 완료.");
    }
}
