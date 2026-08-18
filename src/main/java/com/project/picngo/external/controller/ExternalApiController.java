package com.project.picngo.external.controller;

import com.project.picngo.admin.audit.domain.AdminActionType;
import com.project.picngo.admin.audit.service.AdminAuditLogService;
import com.project.picngo.auth.service.CustomUserDetails;
import com.project.picngo.external.DirectionsClient;
import com.project.picngo.external.WeatherClient;
import com.project.picngo.external.dto.DirectionsResponse;
import com.project.picngo.external.dto.GoldenHourResponse;
import com.project.picngo.external.dto.WeatherForecastResponse;
import com.project.picngo.spot.service.TourApiSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ExternalApiController implements ExternalApiControllerApiSpec {

    private final WeatherClient weatherClient;
    private final DirectionsClient directionsClient;
    private final TourApiSyncService tourApiSyncService;
    private final AdminAuditLogService adminAuditLogService;

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
            @AuthenticationPrincipal CustomUserDetails adminUserDetails,
            @RequestParam int areaCode,
            @RequestParam(required = false) Integer startPage,
            @RequestParam(required = false) Integer endPage
    ) {
        Long adminId = adminUserDetails != null ? adminUserDetails.getId() : null;
        int saved = (startPage != null && endPage != null)
                ? tourApiSyncService.sync(areaCode, startPage, endPage)
                : tourApiSyncService.sync(areaCode);

        try {
            adminAuditLogService.record(
                    adminId,
                    AdminActionType.TOUR_API_SYNC,
                    "TOUR_API",
                    "AREA_" + areaCode,
                    String.format("한국관광공사 TourAPI 지역(areaCode: %d) 동기화 실행 (%d건 저장)", areaCode, saved),
                    null
            );
        } catch (Exception e) {
            log.warn("TourAPI 동기화 감사 로그 기록 실패: {}", e.getMessage());
        }

        return ResponseEntity.ok(saved + "건 저장 완료");
    }

    // 4. TourAPI 전체 지역 동기화 (admin 전용)
    @PostMapping("/admin/tour-api/sync/all")
    public ResponseEntity<String> syncAll(
            @AuthenticationPrincipal CustomUserDetails adminUserDetails
    ) {
        Long adminId = adminUserDetails != null ? adminUserDetails.getId() : null;
        int saved = tourApiSyncService.syncAll();

        try {
            adminAuditLogService.record(
                    adminId,
                    AdminActionType.TOUR_API_SYNC,
                    "TOUR_API",
                    "ALL_AREAS",
                    String.format("한국관광공사 TourAPI 전국 17개 지역 전체 동기화 실행 (%d건 저장)", saved),
                    null
            );
        } catch (Exception e) {
            log.warn("TourAPI 전체 동기화 감사 로그 기록 실패: {}", e.getMessage());
        }

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
