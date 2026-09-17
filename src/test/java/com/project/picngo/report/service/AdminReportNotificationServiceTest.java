package com.project.picngo.report.service;

import com.project.picngo.notification.service.NotificationService;
import com.project.picngo.report.domain.ReportTargetType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminReportNotificationServiceTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private AdminReportNotificationService adminReportNotificationService;

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void sendsPostDeletionNotificationsAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();

        adminReportNotificationService.sendTargetDeletedAfterCommit(
                ReportTargetType.POST,
                List.of(10L, 11L),
                20L,
                100L
        );

        verify(notificationService, never()).sendPushNotification(
                10L,
                "COMMUNITY_REPORT_RESOLVED",
                "신고 처리 결과 안내",
                "신고해 주신 게시물이 운영 정책에 따라 삭제되었습니다.",
                null,
                null,
                "COMMUNITY_REPORT_RESOLVED:100:10"
        );

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);

        verify(notificationService).sendPushNotification(
                10L,
                "COMMUNITY_REPORT_RESOLVED",
                "신고 처리 결과 안내",
                "신고해 주신 게시물이 운영 정책에 따라 삭제되었습니다.",
                null,
                null,
                "COMMUNITY_REPORT_RESOLVED:100:10"
        );
        verify(notificationService).sendPushNotification(
                11L,
                "COMMUNITY_REPORT_RESOLVED",
                "신고 처리 결과 안내",
                "신고해 주신 게시물이 운영 정책에 따라 삭제되었습니다.",
                null,
                null,
                "COMMUNITY_REPORT_RESOLVED:100:11"
        );
        verify(notificationService).sendPushNotification(
                20L,
                "COMMUNITY_POST_REMOVED",
                "게시물 삭제 안내",
                "작성하신 게시물이 운영 정책에 따라 삭제되었습니다.",
                null,
                null,
                "COMMUNITY_POST_REMOVED:100:20"
        );
    }

    @Test
    void sendsReviewDeletionNotificationsImmediatelyWithoutSynchronization() {
        adminReportNotificationService.sendTargetDeletedAfterCommit(
                ReportTargetType.REVIEW,
                List.of(10L),
                20L,
                100L
        );

        verify(notificationService).sendPushNotification(
                10L,
                "COMMUNITY_REPORT_RESOLVED",
                "신고 처리 결과 안내",
                "신고해 주신 리뷰가 운영 정책에 따라 삭제되었습니다.",
                null,
                null,
                "COMMUNITY_REPORT_RESOLVED:100:10"
        );
        verify(notificationService).sendPushNotification(
                20L,
                "COMMUNITY_REVIEW_REMOVED",
                "리뷰 삭제 안내",
                "작성하신 리뷰가 운영 정책에 따라 삭제되었습니다.",
                null,
                null,
                "COMMUNITY_REVIEW_REMOVED:100:20"
        );
    }
}
