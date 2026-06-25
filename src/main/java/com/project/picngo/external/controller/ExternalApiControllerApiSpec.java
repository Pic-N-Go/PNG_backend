package com.project.picngo.external.controller;

import com.project.picngo.external.dto.DirectionsResponse;
import com.project.picngo.external.dto.GoldenHourResponse;
import com.project.picngo.external.dto.WeatherForecastResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Tag(name = "외부 API 연동 (External)", description = "길찾기, 기상청, 일출/일몰 외부 API 통신 컨트롤러")
public interface ExternalApiControllerApiSpec {

    @Operation(summary = "한국관광공사 특정 지역 스팟 동기화", description = "TourAPI에서 특정 지역 관광지 전체를 페이지 순환하여 DB에 저장합니다. areaCode: 1=서울, 6=부산, 39=제주 등")
    ResponseEntity<String> syncSpots(
            @Parameter(description = "지역 코드 (1=서울, 2=인천, 3=대전, 4=대구, 5=광주, 6=부산, 7=울산, 8=세종, 31=경기, 32=강원, 33=충북, 34=충남, 35=경북, 36=경남, 37=전북, 38=전남, 39=제주)") @RequestParam int areaCode
    );

    @Operation(summary = "한국관광공사 전체 지역 스팟 동기화", description = "전국 17개 지역 관광지 데이터를 totalCount 기반 페이지 순환으로 전부 가져옵니다. 최초 1회 실행용.\n\n⚠️ 주의: 스팟 수만큼 detailCommon API를 추가 호출하므로 완료까지 상당한 시간이 소요됩니다.\n중간 업데이트가 필요하거나 특정 지역만 갱신할 경우 POST /tour-api/sync (areaCode 지정)를 사용하세요.")
    ResponseEntity<String> syncAll();

@Operation(summary = "길찾기 (이동시간/거리)", description = "카카오모빌리티 API를 이용하여 출발지에서 목적지까지의 자동차 예상 소요 시간과 거리를 조회합니다.")
    ResponseEntity<DirectionsResponse> getDirections(
            @Parameter(description = "출발지 위도") @RequestParam Double startLat,
            @Parameter(description = "출발지 경도") @RequestParam Double startLng,
            @Parameter(description = "목적지 위도") @RequestParam Double goalLat,
            @Parameter(description = "목적지 경도") @RequestParam Double goalLng
    );

    @Operation(summary = "단기 예보 조회", description = "기상청 API를 이용하여 특정 위치의 날씨 예보를 조회합니다.")
    ResponseEntity<List<WeatherForecastResponse>> getWeatherForecast(
            @Parameter(description = "위도") @RequestParam Double lat,
            @Parameter(description = "경도") @RequestParam Double lng,
            @Parameter(description = "조회할 날짜 (yyyyMMdd)") @RequestParam String date
    );

    @Operation(summary = "명소 골든아워 조회", description = "특정 명소의 일출/일몰 시간을 가져와 골든아워(사진 찍기 좋은 시간)를 계산합니다.")
    ResponseEntity<GoldenHourResponse> getSpotGoldenHour(
            @Parameter(description = "명소 ID") @PathVariable Long id,
            @Parameter(description = "조회 날짜 (yyyy-MM-dd)") @RequestParam String date
    );
}
