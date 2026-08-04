package com.project.picngo.course.dto;

/**
 * 이동시간 계산 결과.
 * 숫자만으로는 카카오 실측인지 우리 추정인지 알 수 없어 출처를 함께 담는다.
 * 계산은 코스 저장 시점에 하고 조회 시점에는 DB에서 꺼내 쓰므로,
 * 저장할 때 같이 기록해두지 않으면 나중에 복원할 수 없다.
 */
public record TravelTimeResult(
        Integer minutes,
        boolean estimated
) {
    /** 카카오 길찾기가 실제로 계산한 값 */
    public static TravelTimeResult measured(Integer minutes) {
        return new TravelTimeResult(minutes, false);
    }

    /** 직선거리 기반 자체 추정값 */
    public static TravelTimeResult estimate(Integer minutes) {
        return new TravelTimeResult(minutes, true);
    }

    /** 산출 불가 (유효 범위 밖 좌표, 스팟 조회 실패 등) */
    public static TravelTimeResult unknown() {
        return new TravelTimeResult(null, false);
    }
}
