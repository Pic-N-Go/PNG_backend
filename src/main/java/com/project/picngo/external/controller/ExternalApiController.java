package com.project.picngo.external.controller;

import com.project.picngo.external.DirectionsClient;
import com.project.picngo.external.WeatherClient;
import com.project.picngo.external.dto.DirectionsResponse;
import com.project.picngo.external.dto.GoldenHourResponse;
import com.project.picngo.external.dto.WeatherForecastResponse;
import com.project.picngo.spot.service.TourApiSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class ExternalApiController implements ExternalApiControllerApiSpec {

    private final WeatherClient weatherClient;
    private final DirectionsClient directionsClient;
    private final TourApiSyncService tourApiSyncService;

    // 1. 길찾기 API (바로 출발 시 호출)
    @GetMapping("/directions")
    public ResponseEntity<DirectionsResponse> getDirections(
            @RequestParam Double startLat,
            @RequestParam Double startLng,
            @RequestParam Double goalLat,
            @RequestParam Double goalLng
    ) {
        return ResponseEntity.ok(directionsClient.getTravelInfo(startLat, startLng, goalLat, goalLng));
    }

    // 2. 단기 예보 조회 (여행 계획 날씨)
    @GetMapping("/weather")
    public ResponseEntity<List<WeatherForecastResponse>> getWeather(
            @RequestParam Double lat,
            @RequestParam Double lng,
            @RequestParam(required = false) String date) {
        if (date == null) {
            date = LocalDate.now(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        }
        return ResponseEntity.ok(weatherClient.getShortTermForecast(lat, lng, date));
    }

    // 3. TourAPI 특정 지역 동기화 (admin 전용, startPage/endPage로 분할 가능)
    @PostMapping("/admin/tour-api/sync")
    public ResponseEntity<String> syncSpots(
            @RequestParam int areaCode,
            @RequestParam(required = false) Integer startPage,
            @RequestParam(required = false) Integer endPage
    ) {
        int saved = (startPage != null && endPage != null)
                ? tourApiSyncService.sync(areaCode, startPage, endPage)
                : tourApiSyncService.sync(areaCode);
        return ResponseEntity.ok(saved + "건 저장 완료");
    }

    // 4. TourAPI 전체 지역 동기화 (admin 전용)
    @PostMapping("/admin/tour-api/sync/all")
    public ResponseEntity<String> syncAll() {
        int saved = tourApiSyncService.syncAll();
        return ResponseEntity.ok("전체 지역 동기화 완료: " + saved + "건 저장");
    }

    // 5. 골든아워 조회 (스팟별/홈 화면)
    @GetMapping("/spots/{id}/golden-hour")
    public ResponseEntity<GoldenHourResponse> getSpotGoldenHour(
            @PathVariable Long id,
            @RequestParam String date
    ) {
        // 실제로는 id(SpotId)를 통해 DB에서 위경도를 조회한 후 API를 호출합니다.
        Double mockLat = 37.5665;
        Double mockLng = 126.9780;
        return ResponseEntity.ok(weatherClient.getGoldenHour(mockLat, mockLng, date));
    }
}
