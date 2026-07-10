package com.project.picngo.spot.controller;

import com.project.picngo.spot.dto.BookmarkResponse;
import com.project.picngo.spot.dto.ChecklistRequest;
import com.project.picngo.spot.dto.ChecklistResponse;
import com.project.picngo.spot.dto.NearbySpotResponse;
import com.project.picngo.spot.dto.PhotogenicResponse;
import com.project.picngo.spot.dto.RecommendedSpotResponse;
import com.project.picngo.spot.dto.ReviewListResponse;
import com.project.picngo.spot.dto.ReviewRequest;
import com.project.picngo.spot.dto.ReviewResponse;
import com.project.picngo.spot.dto.SpotDetailResponse;
import com.project.picngo.spot.dto.SpotPhotoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Tag(name = "스팟 (Spot)", description = "스팟 상세 정보 API")
public interface SpotControllerApiSpec {

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

    @Operation(summary = "스팟 상세 조회", description = "스팟 ID로 상세 정보를 조회합니다. 태그, 편의정보, 체크리스트, 통계, 북마크 여부를 포함합니다.")
    ResponseEntity<SpotDetailResponse> getSpotDetail(
            @Parameter(description = "스팟 ID") @PathVariable Long id
    );

    @Operation(summary = "리뷰 목록 조회", description = "스팟의 리뷰 목록과 요약 정보를 반환합니다. sort: LATEST(기본) | RATING_HIGH | RATING_LOW")
    ResponseEntity<ReviewListResponse> getReviews(
            @Parameter(description = "스팟 ID") @PathVariable Long id,
            @Parameter(description = "정렬 기준") @RequestParam(defaultValue = "LATEST") String sort,
            @Parameter(description = "페이지 번호 (0부터)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "20") int size
    );

    @Operation(summary = "포토제닉 지수 조회", description = "날씨·미세먼지·시즌·골든아워 기반 포토제닉 점수를 반환합니다.")
    ResponseEntity<PhotogenicResponse> getPhotogenicScore(
            @Parameter(description = "스팟 ID") @PathVariable Long id
    );

    @Operation(summary = "북마크 토글", description = "북마크를 추가하거나 취소합니다. isBookmarked: true면 추가됨, false면 취소됨.")
    ResponseEntity<BookmarkResponse> toggleBookmark(
            @Parameter(description = "스팟 ID") @PathVariable Long id
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
            @RequestBody ChecklistRequest request
    );

    @Operation(summary = "체크리스트 항목 삭제", description = "사용자가 직접 추가한 항목만 삭제 가능합니다.")
    ResponseEntity<Void> deleteChecklistItem(
            @Parameter(description = "스팟 ID") @PathVariable Long id,
            @Parameter(description = "항목 ID") @PathVariable Long itemId
    );

    @Operation(summary = "리뷰 작성", description = "스팟에 리뷰를 작성합니다. 사진 업로드는 별도 API로 처리합니다.")
    ResponseEntity<ReviewResponse> createReview(
            @Parameter(description = "스팟 ID") @PathVariable Long id,
            @RequestBody ReviewRequest request
    );
}
