package com.project.picngo.bookmark.controller;

import com.project.picngo.bookmark.dto.BookmarkCollectionResponse;
import com.project.picngo.bookmark.dto.CreateCollectionRequest;
import com.project.picngo.bookmark.dto.SyncCollectionsRequest;
import com.project.picngo.bookmark.service.BookmarkCollectionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class BookmarkCollectionController implements BookmarkCollectionControllerApiSpec {

    private final BookmarkCollectionService bookmarkCollectionService;

    @GetMapping("/bookmark-collections")
    public ResponseEntity<List<BookmarkCollectionResponse>> getCollections(
            @RequestParam(required = false) Long spotId
    ) {
        return ResponseEntity.ok(bookmarkCollectionService.getCollections(spotId));
    }

    @PostMapping("/bookmark-collections")
    public ResponseEntity<BookmarkCollectionResponse> createCollection(
            @Valid @RequestBody CreateCollectionRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bookmarkCollectionService.createCollection(request));
    }

    @PutMapping("/spots/{spotId}/bookmark-collections")
    public ResponseEntity<Void> syncSpotCollections(
            @PathVariable Long spotId,
            @Valid @RequestBody SyncCollectionsRequest request
    ) {
        bookmarkCollectionService.syncSpotCollections(spotId, request.collectionIds());
        return ResponseEntity.noContent().build();
    }
}
