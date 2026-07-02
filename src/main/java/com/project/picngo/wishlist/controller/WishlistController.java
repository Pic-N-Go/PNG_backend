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
    public ResponseEntity<List<WishlistResponse>> getWishlist(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(wishlistService.getWishlist(userDetails.getId()));
    }

    @PostMapping
    public ResponseEntity<WishlistResponse> createWishlist(@AuthenticationPrincipal CustomUserDetails userDetails, @Valid @RequestBody WishlistCreateRequest request) {
        return ResponseEntity.ok(wishlistService.createWishlist(userDetails.getId(), request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<WishlistResponse> getWishlistDetail(@AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable Long id) {
        return ResponseEntity.ok(wishlistService.getWishlistDetail(id, userDetails.getId()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<WishlistResponse> updateWishlist(@AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable Long id, @Valid @RequestBody WishlistUpdateRequest request) {
        return ResponseEntity.ok(wishlistService.updateWishlist(id, userDetails.getId(), request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteWishlist(@AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable Long id) {
        wishlistService.deleteWishlist(id, userDetails.getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/items")
    public ResponseEntity<WishlistItemResponse> addItemToWishlist(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody WishlistItemRequest request) {
        return ResponseEntity.ok(wishlistService.addItemToWishlist(id, userDetails.getId(), request));
    }

    @DeleteMapping("/{id}/items/{itemId}")
    public ResponseEntity<Void> removeItemFromWishlist(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @PathVariable Long itemId) {
        wishlistService.removeItemFromWishlist(id, itemId, userDetails.getId());
        return ResponseEntity.noContent().build();
    }
}
