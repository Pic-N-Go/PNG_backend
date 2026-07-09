package com.project.picngo.spot.controller;

import com.project.picngo.spot.dto.SpotMapResponse;
import com.project.picngo.spot.dto.SpotResponse;
import com.project.picngo.spot.dto.SpotSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Tag(name = "스팟 (Spot)", description = "스팟 목록, 검색, 인기 스팟, 지도 핀 및 요약 카드 조회 API")
public interface SpotControllerApiSpec {

    @Operation(
            summary = "스팟 목록 조회",
            description = "승인된 스팟 목록을 카테고리, 정렬, 페이지 조건에 따라 조회합니다."
    )
    ResponseEntity<Page<SpotResponse>> getSpots(
            @Parameter(description = "스팟 카테고리") @RequestParam(required = false) String category,
            @Parameter(description = "정렬 기준: latest, popular, score") @RequestParam(defaultValue = "latest") String sort,
            @Parameter(description = "페이지 번호") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "20") int size
    );

    @Operation(
            summary = "인기 스팟 조회",
            description = "북마크 수와 리뷰 수를 기준으로 인기 스팟 목록을 조회합니다."
    )
    ResponseEntity<List<SpotResponse>> getPopularSpots(
            @Parameter(description = "스팟 카테고리") @RequestParam(required = false) String category,
            @Parameter(description = "조회할 개수") @RequestParam(defaultValue = "10") int size
    );

    @Operation(
            summary = "스팟 검색",
            description = "검색어를 기준으로 스팟 이름, 주소, 개요를 검색합니다."
    )
    ResponseEntity<Page<SpotResponse>> searchSpots(
            @Parameter(description = "검색어") @RequestParam String keyword,
            @Parameter(description = "스팟 카테고리") @RequestParam(required = false) String category,
            @Parameter(description = "페이지 번호") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "20") int size
    );

    @Operation(
            summary = "지도 영역 내 스팟 핀 조회",
            description = "현재 지도 화면의 남서/북동 좌표 범위 안에 있는 스팟 핀 목록을 조회합니다."
    )
    ResponseEntity<List<SpotMapResponse>> getMapSpots(
            @Parameter(description = "남서쪽 위도") @RequestParam Double southWestLat,
            @Parameter(description = "남서쪽 경도") @RequestParam Double southWestLng,
            @Parameter(description = "북동쪽 위도") @RequestParam Double northEastLat,
            @Parameter(description = "북동쪽 경도") @RequestParam Double northEastLng,
            @Parameter(description = "스팟 카테고리") @RequestParam(required = false) String category
    );

    @Operation(
            summary = "스팟 요약 카드 조회",
            description = "지도 핀 선택 시 하단 요약 카드에 표시할 스팟 정보를 조회합니다."
    )
    ResponseEntity<SpotSummaryResponse> getSpotSummary(
            @Parameter(description = "스팟 ID") @PathVariable Long id
    );
}
