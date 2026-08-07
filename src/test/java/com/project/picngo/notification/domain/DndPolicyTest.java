package com.project.picngo.notification.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class DndPolicyTest {

    private boolean active(LocalTime start, LocalTime end, LocalTime now) {
        return DndPolicy.isActive(true, start, end, now);
    }

    @Test
    @DisplayName("비활성화(false/null)면 항상 DND 아님")
    void disabled() {
        assertThat(DndPolicy.isActive(false, LocalTime.of(22, 0), LocalTime.of(7, 0), LocalTime.of(23, 0))).isFalse();
        assertThat(DndPolicy.isActive(null, LocalTime.of(22, 0), LocalTime.of(7, 0), LocalTime.of(23, 0))).isFalse();
    }

    @Test
    @DisplayName("시작/종료 시간 미설정이면 DND 아님")
    void nullTimes() {
        assertThat(DndPolicy.isActive(true, null, LocalTime.of(7, 0), LocalTime.of(23, 0))).isFalse();
        assertThat(DndPolicy.isActive(true, LocalTime.of(22, 0), null, LocalTime.of(23, 0))).isFalse();
    }

    @Test
    @DisplayName("[엣지 회귀] 시작==종료면 DND 아님 (하루 종일 침묵 방지)")
    void startEqualsEnd() {
        LocalTime t = LocalTime.of(9, 0);
        // 예전 로직은 이 경우 항상 true(하루 종일 DND)였음 → false여야 한다
        assertThat(active(t, t, LocalTime.of(9, 0))).isFalse();
        assertThat(active(t, t, LocalTime.of(15, 0))).isFalse();
        assertThat(active(t, t, LocalTime.of(3, 0))).isFalse();
    }

    @Test
    @DisplayName("같은 날 구간 [start, end): 시작 포함, 종료 제외")
    void sameDayWindow() {
        LocalTime start = LocalTime.of(13, 0);
        LocalTime end = LocalTime.of(15, 0);
        assertThat(active(start, end, LocalTime.of(13, 0))).isTrue();    // 시작 포함
        assertThat(active(start, end, LocalTime.of(14, 0))).isTrue();    // 내부
        assertThat(active(start, end, LocalTime.of(15, 0))).isFalse();   // 종료 제외
        assertThat(active(start, end, LocalTime.of(12, 59))).isFalse();  // 시작 전
    }

    @Test
    @DisplayName("자정 넘김 구간 [start,24)∪[0,end)")
    void overnightWindow() {
        LocalTime start = LocalTime.of(23, 0);
        LocalTime end = LocalTime.of(7, 0);
        assertThat(active(start, end, LocalTime.of(23, 30))).isTrue();  // 밤
        assertThat(active(start, end, LocalTime.of(2, 0))).isTrue();    // 새벽
        assertThat(active(start, end, LocalTime.of(6, 59))).isTrue();   // 종료 직전
        assertThat(active(start, end, LocalTime.of(7, 0))).isFalse();   // 종료(제외)
        assertThat(active(start, end, LocalTime.of(12, 0))).isFalse();  // 낮
    }
}
