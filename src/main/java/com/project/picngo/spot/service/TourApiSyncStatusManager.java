package com.project.picngo.spot.service;

import com.project.picngo.spot.dto.TourApiSyncStageResponse;
import com.project.picngo.spot.dto.TourApiSyncStageStatus;
import com.project.picngo.spot.dto.TourApiSyncStatusResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public class TourApiSyncStatusManager {

    public enum Stage { SPOT, PET, ACCESSIBILITY }

    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private volatile String jobId;
    private volatile String currentJob = "IDLE";
    private volatile Integer currentAreaCode;
    private volatile LocalDateTime startedAt;
    private volatile LocalDateTime lastCompletedAt;
    private volatile String lastError;
    private StageState spot = StageState.pending("일반 Spot 동기화 대기 중");
    private StageState pet = StageState.pending("반려동물 정보 동기화 대기 중");
    private StageState accessibility = StageState.pending("무장애 정보 동기화 대기 중");

    public synchronized boolean tryLock(String jobName, Integer areaCode) {
        if (!isRunning.compareAndSet(false, true)) {
            log.warn("[TourApiSyncLock] 이미 동기화 작업이 실행 중입니다. (현재 실행 중: {})", currentJob);
            return false;
        }
        jobId = UUID.randomUUID().toString();
        currentJob = jobName;
        currentAreaCode = areaCode;
        startedAt = LocalDateTime.now();
        lastError = null;
        spot = StageState.pending("일반 Spot 동기화 시작 대기 중");
        pet = StageState.pending("반려동물 정보 동기화 대기 중");
        accessibility = StageState.pending("무장애 정보 동기화 대기 중");
        log.info("[TourApiSyncLock] 파이프라인 락 획득: jobId={}, job={}, areaCode={}", jobId, jobName, areaCode);
        return true;
    }

    public String currentJobId() {
        return jobId;
    }

    public synchronized void markStageRunning(String messageJobId, Stage stage, String message) {
        if (isCurrent(messageJobId)) state(stage).running(message);
    }

    public synchronized void updateStageProgress(String messageJobId, Stage stage, int processed, int total, String message) {
        if (isCurrent(messageJobId)) state(stage).progress(processed, total, message);
    }

    public synchronized void markStageCompleted(String messageJobId, Stage stage, int processed, String message) {
        if (!isCurrent(messageJobId)) return;
        state(stage).completed(processed, message);
        completePipelineIfReady();
    }

    public synchronized void markStageRetrying(String messageJobId, Stage stage, String errorMessage) {
        if (isCurrent(messageJobId)) state(stage).retrying(errorMessage);
    }

    public synchronized void markStageFailed(String messageJobId, Stage stage, String errorMessage) {
        if (!isCurrent(messageJobId)) return;
        state(stage).failed(errorMessage);
        failPipeline(errorMessage);
    }

    public synchronized void updateProgress(int processed, int total, String message) {
        if (isRunning.get()) spot.progress(processed, total, message);
    }

    public void markSpotCompleted(String messageJobId, int totalProcessed) {
        markStageCompleted(messageJobId, Stage.SPOT, totalProcessed,
                String.format("일반 Spot 동기화 완료 (총 %d건 저장)", totalProcessed));
    }

    /** 이전 단위 테스트와 직접 호출부를 위한 호환 메서드다. */
    public synchronized void markSuccess(int totalProcessed) {
        spot.completed(totalProcessed, String.format("동기화 성공 완료 (총 %d건 저장)", totalProcessed));
        pet.completed(0, "반려동물 정보 동기화 완료");
        accessibility.completed(0, "무장애 정보 동기화 완료");
        completePipelineIfReady();
    }

    public synchronized void markFailed(String errorMessage) {
        if (isRunning.get()) spot.failed(errorMessage);
        failPipeline(errorMessage);
    }

    public synchronized void releaseLock() {
        if (isRunning.getAndSet(false)) {
            log.info("[TourApiSyncLock] 동기화 파이프라인 락 강제 해제: jobId={}", jobId);
        }
    }

    public boolean isRunning() {
        return isRunning.get();
    }

    public synchronized TourApiSyncStatusResponse getStatus() {
        TourApiSyncStageStatus overallStatus = overallStatus();
        return new TourApiSyncStatusResponse(
                isRunning.get(), currentJob, currentAreaCode,
                spot.totalCount, spot.processedCount, percentage(spot.processedCount, spot.totalCount),
                overallMessage(overallStatus), startedAt, lastCompletedAt, lastError,
                jobId, overallStatus, spot.toResponse(), pet.toResponse(), accessibility.toResponse()
        );
    }

    private boolean isCurrent(String messageJobId) {
        boolean current = isRunning.get() && jobId != null && jobId.equals(messageJobId);
        if (!current) {
            log.warn("[TourApiSyncLock] 현재 작업과 다른 상태 갱신 무시: currentJobId={}, messageJobId={}", jobId, messageJobId);
        }
        return current;
    }

    private StageState state(Stage stage) {
        return switch (stage) {
            case SPOT -> spot;
            case PET -> pet;
            case ACCESSIBILITY -> accessibility;
        };
    }

    private void completePipelineIfReady() {
        if (spot.status == TourApiSyncStageStatus.COMPLETED
                && pet.status == TourApiSyncStageStatus.COMPLETED
                && accessibility.status == TourApiSyncStageStatus.COMPLETED) {
            lastCompletedAt = LocalDateTime.now();
            lastError = null;
            isRunning.set(false);
            log.info("[TourApiSyncLock] 전체 동기화 파이프라인 완료: jobId={}", jobId);
        }
    }

    private void failPipeline(String errorMessage) {
        lastCompletedAt = LocalDateTime.now();
        lastError = errorMessage;
        isRunning.set(false);
        log.error("[TourApiSyncLock] 전체 동기화 파이프라인 실패: jobId={}, error={}", jobId, errorMessage);
    }

    private TourApiSyncStageStatus overallStatus() {
        if (spot.status == TourApiSyncStageStatus.FAILED || pet.status == TourApiSyncStageStatus.FAILED
                || accessibility.status == TourApiSyncStageStatus.FAILED) return TourApiSyncStageStatus.FAILED;
        if (!isRunning.get() && spot.status == TourApiSyncStageStatus.COMPLETED
                && pet.status == TourApiSyncStageStatus.COMPLETED
                && accessibility.status == TourApiSyncStageStatus.COMPLETED) return TourApiSyncStageStatus.COMPLETED;
        if (spot.status == TourApiSyncStageStatus.RETRYING || pet.status == TourApiSyncStageStatus.RETRYING
                || accessibility.status == TourApiSyncStageStatus.RETRYING) return TourApiSyncStageStatus.RETRYING;
        return isRunning.get() ? TourApiSyncStageStatus.IN_PROGRESS : TourApiSyncStageStatus.COMPLETED;
    }

    private String overallMessage(TourApiSyncStageStatus status) {
        if (status == TourApiSyncStageStatus.FAILED) return "동기화 실패: " + lastError;
        if (status == TourApiSyncStageStatus.COMPLETED && lastCompletedAt != null) return "전체 동기화 완료";
        if (accessibility.status != TourApiSyncStageStatus.PENDING) return accessibility.message;
        if (pet.status != TourApiSyncStageStatus.PENDING) return pet.message;
        return spot.message;
    }

    private static double percentage(int processed, int total) {
        if (total <= 0) return 0.0;
        return Math.min(100.0, Math.round(((double) processed / total) * 1000.0) / 10.0);
    }

    private static final class StageState {
        private TourApiSyncStageStatus status;
        private int processedCount;
        private int totalCount;
        private String message;
        private String lastError;

        private static StageState pending(String message) {
            StageState state = new StageState();
            state.status = TourApiSyncStageStatus.PENDING;
            state.message = message;
            return state;
        }

        private void running(String message) {
            status = TourApiSyncStageStatus.IN_PROGRESS;
            this.message = message;
            lastError = null;
        }

        private void progress(int processed, int total, String message) {
            status = TourApiSyncStageStatus.IN_PROGRESS;
            processedCount = processed;
            totalCount = total;
            this.message = message;
            lastError = null;
        }

        private void completed(int processed, String message) {
            status = TourApiSyncStageStatus.COMPLETED;
            processedCount = processed;
            totalCount = Math.max(totalCount, processed);
            this.message = message;
            lastError = null;
        }

        private void retrying(String errorMessage) {
            status = TourApiSyncStageStatus.RETRYING;
            message = "재시도 대기 중: " + errorMessage;
            lastError = errorMessage;
        }

        private void failed(String errorMessage) {
            status = TourApiSyncStageStatus.FAILED;
            message = "동기화 실패: " + errorMessage;
            lastError = errorMessage;
        }

        private TourApiSyncStageResponse toResponse() {
            return new TourApiSyncStageResponse(status, processedCount, totalCount,
                    percentage(processedCount, totalCount), message, lastError);
        }
    }
}
