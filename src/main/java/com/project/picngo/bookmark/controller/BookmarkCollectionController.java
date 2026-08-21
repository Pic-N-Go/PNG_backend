package com.project.picngo.bookmark.controller;

import com.project.picngo.auth.service.CustomUserDetails;
import com.project.picngo.bookmark.dto.BookmarkCollectionResponse;
import com.project.picngo.bookmark.dto.CreateCollectionRequest;
import com.project.picngo.bookmark.dto.SyncCollectionsRequest;
import com.project.picngo.bookmark.service.BookmarkCollectionService;
import com.project.picngo.spot.dto.SpotResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class BookmarkCollectionController implements BookmarkCollectionControllerApiSpec {

    private final BookmarkCollectionService bookmarkCollectionService;

    @GetMapping("/bookmark-collections")
    public ResponseEntity<List<BookmarkCollectionResponse>> getCollections(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) Long spotId
    ) {
        return ResponseEntity.ok(bookmarkCollectionService.getCollections(userDetails.getId(), spotId));
    }

    // 마이페이지 계열 경로를 따른다 (/users/me/albums, /users/me/reviews와 같은 자리).
    @GetMapping("/users/me/bookmarked-spots")
    public ResponseEntity<List<SpotResponse>> getBookmarkedSpots(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(bookmarkCollectionService.getBookmarkedSpots(userDetails.getId()));
    }

    @GetMapping("/bookmark-collections/{collectionId}/spots")
    public ResponseEntity<List<SpotResponse>> getCollectionSpots(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long collectionId
    ) {
        return ResponseEntity.ok(bookmarkCollectionService.getCollectionSpots(userDetails.getId(), collectionId));
    }

    @PostMapping("/bookmark-collections")
    public ResponseEntity<BookmarkCollectionResponse> createCollection(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CreateCollectionRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(bookmarkCollectionService.createCollection(userDetails.getId(), request));
    }

    @PutMapping("/spots/{spotId}/bookmark-collections")
    public ResponseEntity<Void> syncSpotCollections(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long spotId,
            @Valid @RequestBody SyncCollectionsRequest request
    ) {
        bookmarkCollectionService.syncSpotCollections(userDetails.getId(), spotId, request.collectionIds());
        return ResponseEntity.noContent().build();
    }
}
