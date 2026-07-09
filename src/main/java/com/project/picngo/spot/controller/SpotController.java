package com.project.picngo.spot.controller;

import com.project.picngo.spot.dto.SpotMapResponse;
import com.project.picngo.spot.dto.SpotResponse;
import com.project.picngo.spot.service.SpotService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/spots")
@RequiredArgsConstructor
public class SpotController {

    private final SpotService spotService;

    @GetMapping
    public ResponseEntity<Page<SpotResponse>> getSpots(
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "latest") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(spotService.getSpots(category, sort, page, size));
    }

    // 인기 스팟 조회 API
    @GetMapping("/popular")
    public ResponseEntity<List<SpotResponse>> getPopularSpots(
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "10") int size
    ){
        return ResponseEntity.ok(spotService.getPopularSpots(category, size));
    }

    // 스팟 검색 API
    @GetMapping("/search")
    public ResponseEntity<Page<SpotResponse>> searchSpots(
            @RequestParam String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(spotService.searchSpots(keyword, category, page, size));
    }

    // 현재 지도 영역 안의 스팟 핀 조회 API
    @GetMapping("/map")
    public ResponseEntity<List<SpotMapResponse>> getMapSpots(
            @RequestParam Double southWestLat,
            @RequestParam Double southWestLng,
            @RequestParam Double northEastLat,
            @RequestParam Double northEastLng,
            @RequestParam(required = false) String category
    ){
        return ResponseEntity.ok(spotService.getMapSpots(
                southWestLat,
                southWestLng,
                northEastLat,
                northEastLng,
                category
        ));
    }
}
