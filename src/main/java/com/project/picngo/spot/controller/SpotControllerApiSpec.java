package com.project.picngo.spot.controller;

import com.project.picngo.spot.dto.BookmarkResponse;
import com.project.picngo.spot.dto.PhotogenicResponse;
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

@Tag(name = "스팟 (Spot)", description = "스팟 상세 정보 API")
public interface SpotControllerApiSpec {

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

    @Operation(summary = "리뷰 작성", description = "스팟에 리뷰를 작성합니다. 사진 업로드는 별도 API로 처리합니다.")
    ResponseEntity<ReviewResponse> createReview(
            @Parameter(description = "스팟 ID") @PathVariable Long id,
            @RequestBody ReviewRequest request
    );
}
