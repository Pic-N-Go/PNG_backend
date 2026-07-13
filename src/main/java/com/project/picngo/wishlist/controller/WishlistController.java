package com.project.picngo.wishlist.controller;

import com.project.picngo.wishlist.dto.*;
import com.project.picngo.wishlist.service.WishlistService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.project.picngo.auth.service.CustomUserDetails;
import java.util.List;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/wishlist")
@RequiredArgsConstructor
public class WishlistController implements WishlistControllerApiSpec {

    private final WishlistService wishlistService;
    @GetMapping
    public ResponseEntity<List<WishlistSettingResponse>> getWishlists(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(wishlistService.getWishlists(userDetails.getId()));
    }

    @GetMapping("/{spotId}")
    public ResponseEntity<WishlistSettingResponse> getWishlistDetail(
            @AuthenticationPrincipal CustomUserDetails userDetails, 
            @PathVariable Long spotId) {
        return ResponseEntity.ok(wishlistService.getWishlistDetail(userDetails.getId(), spotId));
    }

    @PutMapping("/{spotId}")
    public ResponseEntity<WishlistSettingResponse> updateWishlistSettings(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long spotId,
            @Valid @RequestBody WishlistSettingUpdateRequest request) {
        return ResponseEntity.ok(wishlistService.updateWishlistSettings(userDetails.getId(), spotId, request));
    }

    @DeleteMapping("/{spotId}")
    public ResponseEntity<Void> deleteWishlist(
            @AuthenticationPrincipal CustomUserDetails userDetails, 
            @PathVariable Long spotId) {
        wishlistService.deleteWishlist(userDetails.getId(), spotId);
        return ResponseEntity.noContent().build();
    }
}
