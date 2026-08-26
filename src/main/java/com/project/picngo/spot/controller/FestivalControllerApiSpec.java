package com.project.picngo.spot.controller;

import com.project.picngo.spot.dto.FestivalResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

@Tag(name = "축제/행사 (Festival)", description = "전국 지역 축제 및 행사 정보 (진행중/예정/캘린더) API")
public interface FestivalControllerApiSpec {

    @Operation(summary = "축제/행사 목록 조회", description = "진행 상태(ONGOING/UPCOMING/ENDED/ALL) 및 날짜별로 축제/행사 목록을 페이징 조회합니다.")
    ResponseEntity<Page<FestivalResponse>> getFestivals(
            @Parameter(description = "진행 상태 필터 (ONGOING: 진행중, UPCOMING: 예정, ENDED: 종료, ALL: 전체)", example = "ONGOING")
            @RequestParam(required = false, defaultValue = "ONGOING") String status,

            @Parameter(description = "기준 날짜 (YYYY-MM-DD, 기본값: 오늘)", example = "2026-08-24")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,

            @Parameter(description = "페이지 번호 (0부터 시작)", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "페이지 크기", example = "20")
            @RequestParam(defaultValue = "20") int size
    );

    @Operation(summary = "축제/행사 상세 조회", description = "축제/행사 ID로 상세 정보를 조회합니다.")
    ResponseEntity<FestivalResponse> getFestivalById(
            @Parameter(description = "축제(스팟) ID", example = "1")
            @PathVariable Long id
    );
}
