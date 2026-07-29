package com.project.picngo.spot.controller;

import com.project.picngo.auth.service.CustomUserDetails;
import com.project.picngo.spot.dto.ChecklistRequest;
import com.project.picngo.spot.dto.ChecklistResponse;
import com.project.picngo.spot.dto.MapBoundsRequest;
import com.project.picngo.spot.dto.NearbySpotResponse;
import com.project.picngo.spot.dto.PhotogenicResponse;
import com.project.picngo.spot.dto.RecommendedSpotResponse;
import com.project.picngo.spot.dto.ReviewListResponse;
import com.project.picngo.spot.dto.ReviewRequest;
import com.project.picngo.spot.dto.ReviewResponse;
import com.project.picngo.spot.dto.SpotDetailResponse;
import com.project.picngo.spot.dto.SpotPhotoResponse;
import com.project.picngo.spot.dto.SpotMapResponse;
import com.project.picngo.spot.dto.SpotResponse;
import com.project.picngo.spot.dto.SpotSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Tag(name = "스팟 (Spot)", description = "스팟 관련 (목록, 검색, 지도, 상세, 리뷰 등) API")
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
            @ParameterObject @Valid MapBoundsRequest request
    );

    @Operation(
            summary = "스팟 요약 카드 조회",
            description = "지도 핀 선택 시 하단 요약 카드에 표시할 스팟 정보를 조회합니다."
    )
    ResponseEntity<SpotSummaryResponse> getSpotSummary(
            @Parameter(description = "스팟 ID") @PathVariable Long id
    );

    @Operation(summary = "추천 스팟 조회", description = "리뷰+북마크 합산 인기 스팟 중 랜덤으로 반환합니다. limit 기본값 10, 최대 20.")
    ResponseEntity<List<RecommendedSpotResponse>> getRecommendedSpots(
            @Parameter(description = "결과 수 (최대 20)") @RequestParam(defaultValue = "10") int limit
    );

    @Operation(summary = "주변 스팟 조회", description = "현재 위치 기준 반경 내 스팟을 거리 순으로 반환합니다. radiusKm 기본값 5.0, limit 기본값 20.")
    ResponseEntity<List<NearbySpotResponse>> getNearbySpots(
            @Parameter(description = "위도") @RequestParam Double lat,
            @Parameter(description = "경도") @RequestParam Double lng,
            @Parameter(description = "반경 (km)") @RequestParam(defaultValue = "5.0") Double radiusKm,
            @Parameter(description = "최대 결과 수") @RequestParam(defaultValue = "20") int limit
    );

    @Operation(summary = "스팟 상세 조회", description = "스팟 ID로 상세 정보를 조회합니다. 태그, 편의정보, 체크리스트, 통계, 북마크 여부, 내가 쓴 리뷰 ID를 포함합니다.")
    ResponseEntity<SpotDetailResponse> getSpotDetail(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "스팟 ID") @PathVariable Long id
    );

    @Operation(summary = "리뷰 목록 조회", description = "스팟의 리뷰 목록과 요약 정보를 반환합니다. sort: LATEST(기본) | RATING_HIGH | RATING_LOW")
    ResponseEntity<ReviewListResponse> getReviews(
            @Parameter(description = "스팟 ID") @PathVariable Long id,
            @Parameter(description = "정렬 기준") @RequestParam(defaultValue = "LATEST") String sort,
            @Parameter(description = "페이지 번호 (0부터)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "20") int size
    );

    @Operation(summary = "포토제닉 지수 조회", description = "날씨·미세먼지·시즌·골든아워 기반 포토제닉 점수를 반환합니다. date/time 생략 시 현재 시각 기준.")
    ResponseEntity<PhotogenicResponse> getPhotogenicScore(
            @Parameter(description = "스팟 ID") @PathVariable Long id,
            @Parameter(description = "조회 날짜 (yyyy-MM-dd, 생략 시 오늘)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Parameter(description = "조회 시각 (HH:mm, 생략 시 현재)") @RequestParam(required = false) @DateTimeFormat(pattern = "HH:mm") LocalTime time
    );

    @Operation(summary = "스팟 사진 목록 조회", description = "한국관광공사 TourAPI에서 스팟 공식 사진 목록을 실시간 조회합니다. 사용자 등록 스팟은 빈 배열 반환.")
    ResponseEntity<SpotPhotoResponse> getSpotPhotos(
            @Parameter(description = "스팟 ID") @PathVariable Long id
    );

    @Operation(summary = "촬영 체크리스트 조회", description = "시스템 기본 항목(cat3 기반) + 사용자 추가 항목을 반환합니다.")
    ResponseEntity<ChecklistResponse> getChecklist(
            @Parameter(description = "스팟 ID") @PathVariable Long id
    );

    @Operation(summary = "체크리스트 항목 추가", description = "사용자가 체크리스트 항목을 추가합니다. 최대 10개, 내용 20자 이하.")
    ResponseEntity<ChecklistResponse.ChecklistItemDto> addChecklistItem(
            @Parameter(description = "스팟 ID") @PathVariable Long id,
            @Valid @RequestBody ChecklistRequest request
    );

    @Operation(summary = "체크리스트 항목 삭제", description = "사용자가 직접 추가한 항목만 삭제 가능합니다.")
    ResponseEntity<Void> deleteChecklistItem(
            @Parameter(description = "스팟 ID") @PathVariable Long id,
            @Parameter(description = "항목 ID") @PathVariable Long itemId
    );

    @Operation(summary = "기본 체크리스트 항목 숨김", description = "시스템 기본 항목을 사용자별로 숨깁니다. 이미 숨긴 항목이어도 204를 반환합니다(멱등).")
    ResponseEntity<Void> hideDefaultChecklistItem(
            @Parameter(description = "스팟 ID") @PathVariable Long id,
            @Parameter(description = "기본 항목 ID (조회 응답의 defaultItemId)") @PathVariable Integer defaultItemId
    );

    @Operation(summary = "기본 체크리스트 항목 복원", description = "숨긴 기본 항목을 다시 표시합니다. 숨겨져 있지 않았어도 204를 반환합니다(멱등).")
    ResponseEntity<Void> restoreDefaultChecklistItem(
            @Parameter(description = "스팟 ID") @PathVariable Long id,
            @Parameter(description = "기본 항목 ID (조회 응답의 defaultItemId)") @PathVariable Integer defaultItemId
    );

    @Operation(summary = "리뷰 작성", description = "스팟에 리뷰를 작성합니다. 사진은 최대 5장까지 함께 업로드할 수 있습니다. 이미 이 스팟에 리뷰를 작성했으면 409를 반환합니다.")
    ResponseEntity<ReviewResponse> createReview(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "스팟 ID") @PathVariable Long id,
            @Valid @RequestPart("request") ReviewRequest request,
            @Parameter(description = "리뷰 사진 목록, 최대 5장")
            @RequestPart(value = "photos", required = false) List<MultipartFile> photos
    );
}
