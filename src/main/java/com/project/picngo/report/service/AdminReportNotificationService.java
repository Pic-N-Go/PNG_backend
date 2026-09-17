package com.project.picngo.report.service;

import com.project.picngo.notification.service.NotificationService;
import com.project.picngo.report.domain.ReportTargetType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminReportNotificationService {

    private final NotificationService notificationService;

    public void sendTargetDeletedAfterCommit(
            ReportTargetType targetType,
            List<Long> reporterIds,
            Long reportedUserId,
            Long reportId
    ) {
        Runnable notificationTask = () -> {
            reporterIds.forEach(reporterId -> sendReporterNotification(reporterId, reportId, targetType));

            sendReportedUserNotification(reportedUserId, reportId, targetType);
        };

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            notificationTask.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        notificationTask.run();
                    }
                }
        );
    }

    private void sendReporterNotification(Long reporterId, Long reportId, ReportTargetType targetType) {
        String targetSubject = targetSubject(targetType);

        try {
            notificationService.sendPushNotification(
                    reporterId,
                    "COMMUNITY_REPORT_RESOLVED",
                    "신고 처리 결과 안내",
                    "신고해 주신 " + targetSubject + " 운영 정책에 따라 삭제되었습니다.",
                    null,
                    null,
                    "COMMUNITY_REPORT_RESOLVED:" + reportId + ":" + reporterId
            );
        } catch (Exception exception) {
            log.warn(
                    "신고 처리 알림 전송 실패: reportId={}, reporterId={}, message={}",
                    reportId,
                    reporterId,
                    exception.getMessage()
            );
        }
    }

    private void sendReportedUserNotification(Long reportedUserId, Long reportId, ReportTargetType targetType) {
        String targetName = targetName(targetType);
        String targetSubject = targetSubject(targetType);
        String notificationType = notificationType(targetType);

        try {
            notificationService.sendPushNotification(
                    reportedUserId,
                    notificationType,
                    targetName + " 삭제 안내",
                    "작성하신 " + targetSubject + " 운영 정책에 따라 삭제되었습니다.",
                    null,
                    null,
                    notificationType + ":" + reportId + ":" + reportedUserId
            );
        } catch (Exception exception) {
            log.warn(
                    "신고 대상 작성자 삭제 알림 전송 실패: reportId={}, reportedUserId={}, message={}",
                    reportId,
                    reportedUserId,
                    exception.getMessage()
            );
        }
    }

    private String targetName(ReportTargetType targetType) {
        return switch (targetType) {
            case POST -> "게시물";
            case REVIEW -> "리뷰";
            default -> "콘텐츠";
        };
    }

    private String targetSubject(ReportTargetType targetType) {
        return switch (targetType) {
            case POST -> "게시물이";
            case REVIEW -> "리뷰가";
            default -> "콘텐츠가";
        };
    }

    private String notificationType(ReportTargetType targetType) {
        return switch (targetType) {
            case POST -> "COMMUNITY_POST_REMOVED";
            case REVIEW -> "COMMUNITY_REVIEW_REMOVED";
            default -> "COMMUNITY_CONTENT_REMOVED";
        };
    }
}
