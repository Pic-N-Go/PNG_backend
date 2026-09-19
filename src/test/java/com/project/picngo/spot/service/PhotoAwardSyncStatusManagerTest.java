package com.project.picngo.spot.service;

import com.project.picngo.spot.dto.PhotoAwardSyncStatusResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PhotoAwardSyncStatusManagerTest {

    private PhotoAwardSyncStatusManager statusManager;

    @BeforeEach
    void setUp() {
        statusManager = new PhotoAwardSyncStatusManager();
    }

    @Test
    @DisplayName("초기 상태는 IDLE이고 isRunning은 false이다")
    void initialStateIsIdle() {
        PhotoAwardSyncStatusResponse status = statusManager.getStatus();

        assertThat(status.isRunning()).isFalse();
        assertThat(status.currentJob()).isEqualTo("IDLE");
        assertThat(status.totalCount()).isEqualTo(0);
        assertThat(status.processedCount()).isEqualTo(0);
        assertThat(status.createdCount()).isEqualTo(0);
        assertThat(status.enrichedCount()).isEqualTo(0);
        assertThat(status.progressPercent()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("공모전 동기화 락 획득 및 중복 획득 차단 검증")
    void tryLockAndDuplicatePrevention() {
        boolean firstLock = statusManager.tryLock("지역(부산광역시) 공모전 동기화", 26);
        assertThat(firstLock).isTrue();
        assertThat(statusManager.isRunning()).isTrue();

        boolean secondLock = statusManager.tryLock("전국 공모전 전체 동기화", null);
        assertThat(secondLock).isFalse();

        statusManager.updateProgress(30, 10, 20, 100, "진행 중...");
        PhotoAwardSyncStatusResponse runningStatus = statusManager.getStatus();
        assertThat(runningStatus.isRunning()).isTrue();
        assertThat(runningStatus.processedCount()).isEqualTo(30);
        assertThat(runningStatus.createdCount()).isEqualTo(10);
        assertThat(runningStatus.enrichedCount()).isEqualTo(20);
        assertThat(runningStatus.totalCount()).isEqualTo(100);
        assertThat(runningStatus.progressPercent()).isEqualTo(30.0);

        statusManager.markSuccess(100, 40, 60);
        assertThat(statusManager.isRunning()).isFalse();
        assertThat(statusManager.getStatus().lastCompletedAt()).isNotNull();

        boolean thirdLock = statusManager.tryLock("지역(서울특별시) 공모전 동기화", 11);
        assertThat(thirdLock).isTrue();
    }

    @Test
    @DisplayName("동기화 실패 시 에러 메시지 기록 및 락 해제 검증")
    void markFailedRecordsErrorAndReleasesLock() {
        statusManager.tryLock("전국 공모전 동기화", null);
        assertThat(statusManager.isRunning()).isTrue();

        statusManager.markFailed("공공데이터포털 연결 시간 초과");

        PhotoAwardSyncStatusResponse status = statusManager.getStatus();
        assertThat(status.isRunning()).isFalse();
        assertThat(status.lastError()).isEqualTo("공공데이터포털 연결 시간 초과");
        assertThat(status.statusMessage()).contains("공모전 동기화 실패");
    }
}
