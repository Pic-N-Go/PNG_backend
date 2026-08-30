package com.project.picngo.spot.service;

import com.project.picngo.spot.dto.TourApiSyncStatusResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TourApiSyncStatusManagerTest {

    private TourApiSyncStatusManager statusManager;

    @BeforeEach
    void setUp() {
        statusManager = new TourApiSyncStatusManager();
    }

    @Test
    @DisplayName("초기 상태는 IDLE이고 isRunning은 false이다")
    void initialStateIsIdle() {
        TourApiSyncStatusResponse status = statusManager.getStatus();

        assertThat(status.isRunning()).isFalse();
        assertThat(status.currentJob()).isEqualTo("IDLE");
        assertThat(status.totalCount()).isEqualTo(0);
        assertThat(status.processedCount()).isEqualTo(0);
        assertThat(status.progressPercent()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("동기화 작업 락 획득 및 중복 획득 차단 검증")
    void tryLockAndDuplicatePrevention() {
        // 첫 번째 락 획득 성공
        boolean firstLock = statusManager.tryLock("충남(34) 동기화", 34);
        assertThat(firstLock).isTrue();
        assertThat(statusManager.isRunning()).isTrue();

        // 두 번째 락 획득 시도 (동일 작업 또는 다른 작업) -> 실패해야 함
        boolean secondLock = statusManager.tryLock("서울(1) 동기화", 1);
        assertThat(secondLock).isFalse();

        // 진행률 갱신 검증
        statusManager.updateProgress(150, 600, "150/600 저장 중");
        TourApiSyncStatusResponse runningStatus = statusManager.getStatus();
        assertThat(runningStatus.isRunning()).isTrue();
        assertThat(runningStatus.processedCount()).isEqualTo(150);
        assertThat(runningStatus.totalCount()).isEqualTo(600);
        assertThat(runningStatus.progressPercent()).isEqualTo(25.0);

        // 작업 완료 처리 시 락 해제
        statusManager.markSuccess(600);
        assertThat(statusManager.isRunning()).isFalse();
        assertThat(statusManager.getStatus().lastCompletedAt()).isNotNull();

        // 락 해제 후에는 다시 락 획득 가능
        boolean thirdLock = statusManager.tryLock("서울(1) 동기화", 1);
        assertThat(thirdLock).isTrue();
    }

    @Test
    @DisplayName("동기화 실패 시 에러 메시지 기록 및 락 해제 검증")
    void markFailedRecordsErrorAndReleasesLock() {
        statusManager.tryLock("전국 동기화", null);
        assertThat(statusManager.isRunning()).isTrue();

        statusManager.markFailed("네트워크 타임아웃 발생");

        TourApiSyncStatusResponse status = statusManager.getStatus();
        assertThat(status.isRunning()).isFalse();
        assertThat(status.lastError()).isEqualTo("네트워크 타임아웃 발생");
        assertThat(status.statusMessage()).contains("동기화 실패");
    }
}
