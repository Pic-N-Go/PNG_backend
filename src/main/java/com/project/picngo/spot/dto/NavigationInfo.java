package com.project.picngo.spot.dto;

import com.project.picngo.spot.domain.Spot;

import java.util.Objects;

/**
 * 내비/딥링크 전달용 좌표 묶음.
 * 보정 여부와 무관하게 항상 채워서 내려준다(보정이 없으면 원본 좌표).
 * 프론트는 분기 없이 이 좌표를 그대로 길찾기 딥링크에 넘기면 된다.
 */
public record NavigationInfo(
        Double latitude,
        Double longitude,
        String name,
        NavigationStatus status
) {
    public static NavigationInfo of(Spot spot) {
        if (spot == null) {
            return null;
        }

        Coordinate target = spot.getNavigationTarget();
        NavigationStatus status = NavigationStatus.from(spot.getAccessType());

        // NEEDS_ENTRANCE인데 대표 진입점이 없으면 getNavigationTarget()이 원본을 돌려준다.
        // 이때 CORRECTED로 표시하면 "주차장으로 안내" 문구가 원본 좌표와 함께 나가므로 DIRECT로 낮춘다.
        if (status == NavigationStatus.CORRECTED && isSameAsOrigin(target, spot)) {
            status = NavigationStatus.DIRECT;
        }

        return new NavigationInfo(
                target.latitude(),
                target.longitude(),
                target.name(),
                status
        );
    }

    private static boolean isSameAsOrigin(Coordinate target, Spot spot) {
        return Objects.equals(target.latitude(), spot.getLatitude())
                && Objects.equals(target.longitude(), spot.getLongitude());
    }
}
