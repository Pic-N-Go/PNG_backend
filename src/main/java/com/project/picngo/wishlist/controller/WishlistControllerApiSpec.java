package com.project.picngo.wishlist.controller;

import com.project.picngo.wishlist.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.project.picngo.auth.service.CustomUserDetails;
import java.util.List;

@Tag(name = "위시리스트 (Wishlist)", description = "사용자 찜(위시리스트) 폴더 및 장소 관리 API")
public interface WishlistControllerApiSpec {

    @Operation(summary = "내 위시리스트 목록 조회", description = "사용자가 위시리스트에 담은 모든 스팟 목록과 설정을 조회합니다.")
    ResponseEntity<List<WishlistSettingResponse>> getWishlists(@Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails);

    @Operation(summary = "위시리스트 상세 설정 조회", description = "특정 스팟의 위시리스트 알림 설정을 조회합니다.")
    ResponseEntity<WishlistSettingResponse> getWishlistDetail(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails, 
            @Parameter(description = "스팟 ID") @PathVariable Long spotId
    );

    @Operation(summary = "위시리스트 설정 저장 (추가/수정)", description = "특정 스팟의 위시리스트 설정을 저장합니다. 기존에 없으면 새로 생성하고, 있으면 덮어씁니다.")
    ResponseEntity<WishlistSettingResponse> updateWishlistSettings(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "스팟 ID") @PathVariable Long spotId,
            @RequestBody WishlistSettingUpdateRequest request
    );

    @Operation(summary = "위시리스트에서 장소 제거", description = "특정 스팟을 위시리스트에서 완전히 삭제합니다.")
    ResponseEntity<Void> deleteWishlist(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails, 
            @Parameter(description = "스팟 ID") @PathVariable Long spotId
    );
}
