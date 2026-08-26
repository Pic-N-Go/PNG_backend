package com.project.picngo.spot.service;

import com.project.picngo.spot.dto.TourApiSyncStatusResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public class TourApiSyncStatusManager {

    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private volatile String currentJob = "IDLE";
    private volatile Integer currentAreaCode = null;
    private volatile int totalCount = 0;
    private volatile int processedCount = 0;
    private volatile String statusMessage = "대기 중 (진행 중인 동기화 작업 없음)";
    private volatile LocalDateTime startedAt = null;
    private volatile LocalDateTime lastCompletedAt = null;
    private volatile String lastError = null;

    public boolean tryLock(String jobName, Integer areaCode) {
        if (isRunning.compareAndSet(false, true)) {
            this.currentJob = jobName;
            this.currentAreaCode = areaCode;
            this.totalCount = 0;
            this.processedCount = 0;
            this.startedAt = LocalDateTime.now();
            this.lastError = null;
            this.statusMessage = jobName + " 시작 대기 중 (큐에서 작업 수신 중...)";
            log.info("[TourApiSyncLock] 동기화 작업 락 획득 성공: job={}, areaCode={}", jobName, areaCode);
            return true;
        }
        log.warn("[TourApiSyncLock] 이미 동기화 작업이 실행 중입니다. (현재 실행 중: {})", currentJob);
        return false;
    }

    public void updateProgress(int processed, int total, String message) {
        this.processedCount = processed;
        this.totalCount = total;
        this.statusMessage = message;
    }

    public void markSuccess(int totalProcessed) {
        this.lastCompletedAt = LocalDateTime.now();
        this.lastError = null;
        this.statusMessage = String.format("동기화 성공 완료 (총 %d건 저장)", totalProcessed);
        this.isRunning.set(false);
        log.info("[TourApiSyncLock] 동기화 작업 성공 및 락 해제: job={}, 총 {}건", currentJob, totalProcessed);
    }

    public void markFailed(String errorMessage) {
        this.lastCompletedAt = LocalDateTime.now();
        this.lastError = errorMessage;
        this.statusMessage = "동기화 실패: " + errorMessage;
        this.isRunning.set(false);
        log.error("[TourApiSyncLock] 동기화 작업 실패 및 락 해제: job={}, error={}", currentJob, errorMessage);
    }

    public void releaseLock() {
        if (this.isRunning.getAndSet(false)) {
            log.info("[TourApiSyncLock] 동기화 작업 락 강제 해제");
        }
    }

    public boolean isRunning() {
        return isRunning.get();
    }

    public TourApiSyncStatusResponse getStatus() {
        double progressPercent = 0.0;
        if (totalCount > 0) {
            progressPercent = Math.min(100.0, Math.round(((double) processedCount / totalCount) * 1000.0) / 10.0);
        }

        return new TourApiSyncStatusResponse(
                isRunning.get(),
                currentJob,
                currentAreaCode,
                totalCount,
                processedCount,
                progressPercent,
                statusMessage,
                startedAt,
                lastCompletedAt,
                lastError
        );
    }
}
