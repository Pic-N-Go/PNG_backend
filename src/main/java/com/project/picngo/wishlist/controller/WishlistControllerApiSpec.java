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

    @Operation(summary = "내 위시리스트 목록 조회", description = "사용자가 생성한 모든 위시리스트 폴더와 내부 아이템을 조회합니다.")
    ResponseEntity<List<WishlistResponse>> getWishlist(@Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails);

    @Operation(summary = "위시리스트 생성", description = "새로운 위시리스트(폴더)를 생성합니다.")
    ResponseEntity<WishlistResponse> createWishlist(@Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails, @RequestBody WishlistCreateRequest request);

    @Operation(summary = "위시리스트 상세 조회", description = "특정 위시리스트 폴더 안의 항목을 조회합니다.")
    ResponseEntity<WishlistResponse> getWishlistDetail(@Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails, @Parameter(description = "위시리스트 ID") @PathVariable Long id);

    @Operation(summary = "위시리스트 수정", description = "위시리스트 폴더의 이름을 수정합니다.")
    ResponseEntity<WishlistResponse> updateWishlist(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "위시리스트 ID") @PathVariable Long id,
            @RequestBody WishlistUpdateRequest request
    );

    @Operation(summary = "위시리스트 삭제", description = "위시리스트 폴더 전체를 삭제합니다.")
    ResponseEntity<Void> deleteWishlist(@Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails, @Parameter(description = "위시리스트 ID") @PathVariable Long id);

    @Operation(summary = "위시리스트에 장소 추가", description = "특정 위시리스트 폴더에 새로운 장소(아이템)와 알림 조건을 추가합니다.")
    ResponseEntity<WishlistItemResponse> addItemToWishlist(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "위시리스트 ID") @PathVariable Long id,
            @RequestBody WishlistItemRequest request
    );

    @Operation(summary = "위시리스트에서 장소 제거", description = "위시리스트 폴더에서 특정 장소를 삭제합니다.")
    ResponseEntity<Void> removeItemFromWishlist(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "위시리스트 ID") @PathVariable Long id,
            @Parameter(description = "아이템 ID") @PathVariable Long itemId
    );
}
