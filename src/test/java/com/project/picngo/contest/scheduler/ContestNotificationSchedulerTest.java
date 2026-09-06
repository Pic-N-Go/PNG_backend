package com.project.picngo.contest.scheduler;

import com.project.picngo.contest.domain.Contest;
import com.project.picngo.contest.repository.ContestRepository;
import com.project.picngo.contest.service.ContestService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContestNotificationSchedulerTest {

    @Mock
    private ContestRepository contestRepository;

    @Mock
    private ContestService contestService;

    @InjectMocks
    private ContestNotificationScheduler scheduler;

    @Test
    @DisplayName("매일 09시 스케줄러 실행 시 시작/결과발표 시점이 지난 미발송 콘테스트가 있으면 각각 알림을 발송한다")
    void sendsNotificationsWhenDueContestsExist() {
        Contest contest1 = Contest.create("봄꽃 사진전", "설명", "url", LocalDateTime.now(), 3, 3);
        ReflectionTestUtils.setField(contest1, "id", 101L);

        Contest contest2 = Contest.create("야경 사진전", "설명", "url", LocalDateTime.now().minusWeeks(5), 3, 3);
        ReflectionTestUtils.setField(contest2, "id", 102L);

        given(contestRepository.findAllByActiveTrueAndStartNotificationSentFalseAndSubmitStartAtLessThanEqual(any()))
                .willReturn(List.of(contest1));
        given(contestRepository.findAllByActiveTrueAndResultNotificationSentFalseAndResultOpenAtLessThanEqual(any()))
                .willReturn(List.of(contest2));
        given(contestService.sendStartNotification(101L)).willReturn(5);
        given(contestService.sendResultNotification(102L)).willReturn(8);

        scheduler.sendDailyContestNotifications();

        verify(contestService, times(1)).sendStartNotification(101L);
        verify(contestService, times(1)).sendResultNotification(102L);
    }

    @Test
    @DisplayName("발송 대상 콘테스트가 없으면 알림을 발송하지 않는다")
    void doNothingWhenNoDueContests() {
        given(contestRepository.findAllByActiveTrueAndStartNotificationSentFalseAndSubmitStartAtLessThanEqual(any()))
                .willReturn(List.of());
        given(contestRepository.findAllByActiveTrueAndResultNotificationSentFalseAndResultOpenAtLessThanEqual(any()))
                .willReturn(List.of());

        scheduler.sendDailyContestNotifications();

        verify(contestService, never()).sendStartNotification(any());
        verify(contestService, never()).sendResultNotification(any());
    }
}
