package com.project.picngo.contest.scheduler;

import com.project.picngo.contest.domain.Contest;
import com.project.picngo.contest.repository.ContestRepository;
import com.project.picngo.contest.service.ContestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ContestNotificationScheduler {

    private final ContestRepository contestRepository;
    private final ContestService contestService;

    /**
     * 매일 오전 9시(KST)에 동작하여:
     * 1. 출품 시작 시점이 도래했으나 아직 알림이 발송되지 않은 콘테스트의 시작 알림을 발송합니다.
     * 2. 결과 발표 시점이 도래했으나 아직 알림이 발송되지 않은 콘테스트의 결과 알림을 발송합니다.
     */
    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
    @Transactional
    public void sendDailyContestNotifications() {
        LocalDateTime now = LocalDateTime.now(Contest.ZONE);

        // 1. 출품 시작 알림 발송
        List<Contest> startTargets = contestRepository
                .findAllByActiveTrueAndStartNotificationSentFalseAndSubmitStartAtLessThanEqual(now);
        for (Contest contest : startTargets) {
            try {
                int sentCount = contestService.sendStartNotification(contest.getId());
                log.info("[콘테스트 알림 스케줄러] 콘테스트 시작 알림 발송 완료: contestId={}, title='{}', 발송 건수={}",
                        contest.getId(), contest.getTitle(), sentCount);
            } catch (Exception e) {
                log.error("[콘테스트 알림 스케줄러] 콘테스트 시작 알림 발송 실패: contestId={}, error={}",
                        contest.getId(), e.getMessage(), e);
            }
        }

        // 2. 결과 발표 알림 발송
        List<Contest> resultTargets = contestRepository
                .findAllByActiveTrueAndResultNotificationSentFalseAndResultOpenAtLessThanEqual(now);
        for (Contest contest : resultTargets) {
            try {
                int sentCount = contestService.sendResultNotification(contest.getId());
                log.info("[콘테스트 알림 스케줄러] 콘테스트 결과 발표 알림 발송 완료: contestId={}, title='{}', 발송 건수={}",
                        contest.getId(), contest.getTitle(), sentCount);
            } catch (Exception e) {
                log.error("[콘테스트 알림 스케줄러] 콘테스트 결과 발표 알림 발송 실패: contestId={}, error={}",
                        contest.getId(), e.getMessage(), e);
            }
        }
    }
}
