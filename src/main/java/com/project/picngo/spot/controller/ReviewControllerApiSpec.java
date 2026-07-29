package com.project.picngo.spot.controller;

import com.project.picngo.auth.service.CustomUserDetails;
import com.project.picngo.spot.dto.ReviewPhotoResponse;
import com.project.picngo.spot.dto.ReviewRequest;
import com.project.picngo.spot.dto.ReviewResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "리뷰 (Review)", description = "리뷰 수정/삭제 및 사진 추가/삭제 API")
public interface ReviewControllerApiSpec {

    @Operation(summary = "리뷰 수정", description = "본인 리뷰만 수정할 수 있습니다.")
    ResponseEntity<ReviewResponse> updateReview(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "리뷰 ID") @PathVariable Long id,
            @RequestBody ReviewRequest request
    );

    @Operation(summary = "리뷰 사진 추가",
            description = "본인 리뷰에만 추가할 수 있습니다. 기존 사진 + 신규 파일 합계가 5장을 넘으면 400.")
    ResponseEntity<List<ReviewPhotoResponse>> addReviewPhotos(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "리뷰 ID") @PathVariable Long id,
            @RequestPart("photos") List<MultipartFile> photos
    );

    @Operation(summary = "리뷰 사진 삭제",
            description = "본인 리뷰의 사진만 삭제할 수 있습니다. 다른 리뷰의 photoId면 404.")
    ResponseEntity<Void> deleteReviewPhoto(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "리뷰 ID") @PathVariable Long id,
            @Parameter(description = "사진 ID") @PathVariable Long photoId
    );

    @Operation(summary = "리뷰 삭제", description = "본인 리뷰만 삭제할 수 있습니다.")
    ResponseEntity<Void> deleteReview(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "리뷰 ID") @PathVariable Long id
    );
}
