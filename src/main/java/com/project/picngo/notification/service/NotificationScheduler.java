package com.project.picngo.notification.service;

import com.project.picngo.notification.domain.NotificationSetting;
import com.project.picngo.notification.repository.NotificationSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationScheduler {

    private final NotificationSettingRepository notificationSettingRepository;
    private final FcmService fcmService;

    // 매일 아침 7시에 실행
    @Scheduled(cron = "0 0 7 * * *")
    public void scheduleDailyPushNotifications() {
        log.info("매일 아침 7시 알림 스케줄러 실행 시작...");

        List<NotificationSetting> activeSettings = notificationSettingRepository.findActiveSettingsWithToken().stream()
                .filter(setting -> !isDndActive(setting))
                .toList();

        for (NotificationSetting setting : activeSettings) {
            // TODO: 해당 유저의 위시리스트(WishlistItem) 좌표 및 날씨/일출 API(WeatherClient) 연동을 통한 조건 부합 여부 판단 로직
            
            // 임시 푸시 알림 발송 (테스트용)
            String token = setting.getFcmToken();
            String title = "오늘의 추천 여행지 알림 ☀️";
            String body = "회원님의 위시리스트 날씨 조건과 완벽하게 일치하는 날입니다!";
            
            try {
                fcmService.sendMessage(token, title, body, null);
                log.info("유저 {} 에게 스케줄러 푸시 알림 발송 완료", setting.getUserId());
            } catch (Exception e) {
                log.error("유저 {} 에게 스케줄러 푸시 알림 발송 중 오류 발생", setting.getUserId(), e);
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
