package com.project.picngo.spot.service;

import com.project.picngo.spot.dto.PhotoAwardSyncStatusResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public class PhotoAwardSyncStatusManager {

    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private volatile String currentJob = "IDLE";
    private volatile Integer currentLDongRegnCd = null;
    private volatile int totalCount = 0;
    private volatile int processedCount = 0;
    private volatile int createdCount = 0;
    private volatile int enrichedCount = 0;
    private volatile String statusMessage = "대기 중 (진행 중인 사진공모전 동기화 작업 없음)";
    private volatile LocalDateTime startedAt = null;
    private volatile LocalDateTime lastCompletedAt = null;
    private volatile String lastError = null;

    public boolean tryLock(String jobName, Integer lDongRegnCd) {
        if (isRunning.compareAndSet(false, true)) {
            this.currentJob = jobName;
            this.currentLDongRegnCd = lDongRegnCd;
            this.totalCount = 0;
            this.processedCount = 0;
            this.createdCount = 0;
            this.enrichedCount = 0;
            this.startedAt = LocalDateTime.now();
            this.lastError = null;
            this.statusMessage = jobName + " 시작 대기 중 (큐에서 작업 수신 중...)";
            log.info("[PhotoAwardSyncLock] 공모전 동기화 락 획득 성공: job={}, lDongRegnCd={}", jobName, lDongRegnCd);
            return true;
        }
        log.warn("[PhotoAwardSyncLock] 이미 공모전 동기화 작업이 실행 중입니다. (현재 실행 중: {})", currentJob);
        return false;
    }

    public void updateProgress(int processed, int created, int enriched, int total, String message) {
        this.processedCount = processed;
        this.createdCount = created;
        this.enrichedCount = enriched;
        this.totalCount = total;
        this.statusMessage = message;
    }

    public void markSuccess(int totalProcessed, int totalCreated, int totalEnriched) {
        this.lastCompletedAt = LocalDateTime.now();
        this.lastError = null;
        this.statusMessage = String.format("공모전 동기화 성공 완료 (총 %d건 처리: 신규 %d건, 기존 보강 %d건)",
                totalProcessed, totalCreated, totalEnriched);
        this.isRunning.set(false);
        log.info("[PhotoAwardSyncLock] 공모전 동기화 성공 및 락 해제: job={}, 총 {}건 (신규 {}, 보강 {})",
                currentJob, totalProcessed, totalCreated, totalEnriched);
    }

    public void markSuccess(int totalProcessed) {
        markSuccess(totalProcessed, this.createdCount, this.enrichedCount);
    }

    public void markFailed(String errorMessage) {
        this.lastCompletedAt = LocalDateTime.now();
        this.lastError = errorMessage;
        this.statusMessage = "공모전 동기화 실패: " + errorMessage;
        this.isRunning.set(false);
        log.error("[PhotoAwardSyncLock] 공모전 동기화 실패 및 락 해제: job={}, error={}", currentJob, errorMessage);
    }

    public void releaseLock() {
        if (this.isRunning.getAndSet(false)) {
            log.info("[PhotoAwardSyncLock] 공모전 동기화 락 강제 해제");
        }
    }

    public boolean isRunning() {
        return isRunning.get();
    }

    public PhotoAwardSyncStatusResponse getStatus() {
        double progressPercent = 0.0;
        if (totalCount > 0) {
            progressPercent = Math.min(100.0, Math.round(((double) processedCount / totalCount) * 1000.0) / 10.0);
        }

        return new PhotoAwardSyncStatusResponse(
                isRunning.get(),
                currentJob,
                currentLDongRegnCd,
                totalCount,
                processedCount,
                createdCount,
                enrichedCount,
                progressPercent,
                statusMessage,
                startedAt,
                lastCompletedAt,
                lastError
        );
    }
}
