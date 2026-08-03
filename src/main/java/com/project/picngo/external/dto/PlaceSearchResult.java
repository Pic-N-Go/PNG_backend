package com.project.picngo.external.dto;

import com.project.picngo.spot.dto.Coordinate;

/**
 * 장소 검색 결과.
 * "주변에 진짜 없음(NOT_FOUND)"과 "호출이 실패함(ERROR)"을 구분하기 위한 타입이다.
 * 둘을 null 하나로 뭉개면 일시적 장애가 영구적인 보정 실패로 굳어진다.
 */
public record PlaceSearchResult(
        Status status,
        Coordinate place
) {
    public enum Status {
        /** 검색 성공, 결과 있음 */
        FOUND,
        /** 검색 성공, 결과 0건. 재시도해도 결과가 달라지지 않는다 */
        NOT_FOUND,
        /** 권한/네트워크/타임아웃 등 호출 실패. 재시도 여지가 있다 */
        ERROR
    }

    public static PlaceSearchResult found(Coordinate place) {
        return new PlaceSearchResult(Status.FOUND, place);
    }

    public static PlaceSearchResult notFound() {
        return new PlaceSearchResult(Status.NOT_FOUND, null);
    }

    public static PlaceSearchResult error() {
        return new PlaceSearchResult(Status.ERROR, null);
    }

    public boolean isFound() {
        return status == Status.FOUND;
    }

    public boolean isError() {
        return status == Status.ERROR;
    }
}
