package com.project.picngo.spot.consumer;

import com.project.picngo.admin.audit.domain.AdminActionType;
import com.project.picngo.admin.audit.service.AdminAuditLogService;
import com.project.picngo.spot.config.TourApiRabbitMQConfig;
import com.project.picngo.spot.dto.TourApiSyncMessage;
import com.project.picngo.spot.service.TourApiSyncService;
import com.project.picngo.spot.service.TourApiSyncStatusManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TourApiSyncConsumer {

    private final TourApiSyncService tourApiSyncService;
    private final TourApiSyncStatusManager syncStatusManager;
    private final AdminAuditLogService adminAuditLogService;

    @RabbitListener(queues = TourApiRabbitMQConfig.QUEUE_NAME)
    public void consume(TourApiSyncMessage message) {
        log.info("[TourApiSyncConsumer] 동기화 큐 메시지 수신: scope={}, areaCode={}, adminId={}",
                message.syncType(), message.areaCode(), message.adminId());

        int saved = 0;
        try {
            switch (message.syncType()) {
                case AREA -> {
                    int areaCode = message.areaCode();
                    saved = (message.startPage() != null && message.endPage() != null)
                            ? tourApiSyncService.sync(areaCode, message.startPage(), message.endPage())
                            : tourApiSyncService.sync(areaCode);

                    recordAuditLog(message.adminId(), "AREA_" + areaCode,
                            String.format("한국관광공사 TourAPI 지역(areaCode: %d) 비동기 동기화 완료 (%d건 저장)", areaCode, saved));
                }
                case ALL -> {
                    saved = tourApiSyncService.syncAll();
                    recordAuditLog(message.adminId(), "ALL_AREAS",
                            String.format("한국관광공사 TourAPI 전국 17개 지역 전체 비동기 동기화 완료 (%d건 저장)", saved));
                }
                case SAMPLE -> {
                    int count = message.countPerType() != null ? message.countPerType() : 7;
                    saved = tourApiSyncService.syncSample(count);
                    recordAuditLog(message.adminId(), "SAMPLE",
                            String.format("한국관광공사 TourAPI 타입별 샘플(%d건) 비동기 동기화 완료 (%d건 저장)", count, saved));
                }
            }
            syncStatusManager.markSuccess(saved);
            log.info("[TourApiSyncConsumer] 동기화 작업 완료 처리: scope={}, saved={}", message.syncType(), saved);
        } catch (Exception e) {
            log.error("[TourApiSyncConsumer] 동기화 작업 실패: scope={}, error={}", message.syncType(), e.getMessage(), e);
            syncStatusManager.markFailed(e.getMessage());
        } finally {
            syncStatusManager.releaseLock();
        }
    }

    private void recordAuditLog(Long adminId, String target, String details) {
        try {
            adminAuditLogService.record(
                    adminId,
                    AdminActionType.TOUR_API_SYNC,
                    "TOUR_API",
                    target,
                    details,
                    null
            );
        } catch (Exception e) {
            log.warn("TourAPI 비동기 동기화 감사 로그 기록 실패: {}", e.getMessage());
        }
    }
}
