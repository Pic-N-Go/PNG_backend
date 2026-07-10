package com.project.picngo.spot.controller;

import com.project.picngo.spot.dto.ReviewRequest;
import com.project.picngo.spot.dto.ReviewResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "리뷰 (Review)", description = "리뷰 수정/삭제 API")
public interface ReviewControllerApiSpec {

    @Operation(summary = "리뷰 수정", description = "본인 리뷰만 수정할 수 있습니다.")
    ResponseEntity<ReviewResponse> updateReview(
            @Parameter(description = "리뷰 ID") @PathVariable Long id,
            @RequestBody ReviewRequest request
    );

    @Operation(summary = "리뷰 삭제", description = "본인 리뷰만 삭제할 수 있습니다.")
    ResponseEntity<Void> deleteReview(
            @Parameter(description = "리뷰 ID") @PathVariable Long id
    );
}
