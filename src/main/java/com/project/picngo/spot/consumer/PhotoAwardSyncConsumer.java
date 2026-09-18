package com.project.picngo.spot.consumer;

import com.project.picngo.admin.audit.domain.AdminActionType;
import com.project.picngo.admin.audit.service.AdminAuditLogService;
import com.project.picngo.external.LegalDongMapper;
import com.project.picngo.spot.config.PhotoAwardRabbitMQConfig;
import com.project.picngo.spot.dto.PhotoAwardSyncMessage;
import com.project.picngo.spot.service.PhotoAwardSyncService;
import com.project.picngo.spot.service.PhotoAwardSyncStatusManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PhotoAwardSyncConsumer {

    private final PhotoAwardSyncService photoAwardSyncService;
    private final PhotoAwardSyncStatusManager syncStatusManager;
    private final AdminAuditLogService adminAuditLogService;

    @RabbitListener(queues = PhotoAwardRabbitMQConfig.QUEUE_NAME)
    public void consume(PhotoAwardSyncMessage message) {
        log.info("[PhotoAwardSyncConsumer] 공모전 동기화 큐 메시지 수신: scope={}, lDongRegnCd={}, adminId={}",
                message.syncType(), message.lDongRegnCd(), message.adminId());

        int saved = 0;
        try {
            switch (message.syncType()) {
                case AREA -> {
                    int ldongCd = message.lDongRegnCd();
                    String regionName = LegalDongMapper.getRegionName(ldongCd);
                    saved = photoAwardSyncService.sync(ldongCd);
                    recordAuditLog(message.adminId(), "AREA_" + ldongCd,
                            String.format("사진공모전 수상작 지역(%s) 비동기 동기화 완료 (%d건 처리)", regionName, saved));
                }
                case ALL -> {
                    saved = photoAwardSyncService.syncAll();
                    recordAuditLog(message.adminId(), "ALL_AREAS",
                            String.format("사진공모전 수상작 전국 17개 지역 전체 비동기 동기화 완료 (%d건 처리)", saved));
                }
            }
            syncStatusManager.markSuccess(saved);
            log.info("[PhotoAwardSyncConsumer] 공모전 동기화 완료 처리: scope={}, saved={}", message.syncType(), saved);
        } catch (Exception e) {
            log.error("[PhotoAwardSyncConsumer] 공모전 동기화 작업 실패: scope={}, lDongRegnCd={}, cause={}",
                    message.syncType(), message.lDongRegnCd(), e.getMessage(), e);
            syncStatusManager.markFailed(e.getMessage());
            recordAuditLog(message.adminId(), getTarget(message),
                    String.format("사진공모전 수상작 비동기 동기화 실패 (오류: %s)", e.getMessage()));
        } finally {
            syncStatusManager.releaseLock();
        }
    }

    private String getTarget(PhotoAwardSyncMessage message) {
        if (message == null || message.syncType() == null) return "UNKNOWN";
        return switch (message.syncType()) {
            case AREA -> "AREA_" + message.lDongRegnCd();
            case ALL -> "ALL_AREAS";
        };
    }

    private void recordAuditLog(Long adminId, String target, String details) {
        try {
            adminAuditLogService.record(
                    adminId,
                    AdminActionType.PHOTO_AWARD_SYNC,
                    "PHOTO_AWARD",
                    target,
                    details,
                    null
            );
        } catch (Exception e) {
            log.warn("[PhotoAwardSyncConsumer] 공모전 비동기 동기화 감사 로그 기록 실패: {}", e.getMessage());
        }
    }
}
