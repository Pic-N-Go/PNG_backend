package com.project.picngo.spot.dto;

/**
 * 위도, 경도, 장소명 3개를 깔끔하게 세트로 묶어서 전달하기 위한 DTO
 */
public record Coordinate(
        Double latitude,
        Double longitude,
        String name
) {}
