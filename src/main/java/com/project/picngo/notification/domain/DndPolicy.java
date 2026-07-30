package com.project.picngo.notification.domain;

import java.time.LocalTime;

/**
 * 방해금지(DND) 시간대 판정 정책.
 * 엔티티(NotificationSetting)와 DTO(NotificationSettingResponse)가 동일한 규칙을 쓰도록 한 곳에 모은다.
 */
public final class DndPolicy {

    private DndPolicy() {
    }

    /**
     * 주어진 시각(now)이 DND 구간에 속하는지 판정한다.
     * <ul>
     *   <li>시작 포함, 종료 제외: {@code [start, end)}</li>
     *   <li>start &gt; end 이면 자정을 넘긴 구간: {@code [start, 24:00) ∪ [00:00, end)}</li>
     *   <li>start == end 이면 유효한 구간이 아니므로 DND 아님 (전체 시간 침묵 방지)</li>
     * </ul>
     */
    public static boolean isActive(Boolean enabled, LocalTime start, LocalTime end, LocalTime now) {
        if (!Boolean.TRUE.equals(enabled)) return false;
        if (start == null || end == null || now == null) return false;
        if (start.equals(end)) return false; // 시작==종료: 구간 없음으로 간주 → 하루 종일 DND 되는 버그 방지

        if (start.isBefore(end)) {
            return !now.isBefore(start) && now.isBefore(end);
        }
        // 자정을 넘긴 구간 (예: 23:00 ~ 07:00)
        return !now.isBefore(start) || now.isBefore(end);
    }
}
