package com.project.picngo.community.controller;

import com.project.picngo.auth.service.CustomUserDetails;
import com.project.picngo.community.domain.PostSort;
import com.project.picngo.community.dto.*;
import com.project.picngo.community.service.CommunityPostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/posts")
@RequiredArgsConstructor
public class CommunityPostController {

    private final CommunityPostService postService;

    @GetMapping
    public ResponseEntity<PostPageResponse> getPosts(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "POPULAR") PostSort sort,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(postService.getPosts(userId(userDetails), sort, keyword, page, size));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PostResponse> createPost(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestPart("request") PostCreateRequest request,
            @RequestPart("images") List<MultipartFile> images
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(postService.createPost(userDetails.getId(), request, images));
    }

    @PatchMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PostResponse> updatePost(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestPart("request") PostUpdateRequest request,
            @RequestPart(value = "newImages", required = false) List<MultipartFile> newImages
    ) {
        return ResponseEntity.ok(postService.updatePost(id, userDetails.getId(), request, newImages));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePost(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id
    ) {
        postService.deletePost(id, userDetails.getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<PostResponse> getPost(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(postService.getPost(id, userId(userDetails)));
    }

    @PostMapping("/{id}/like")
    public ResponseEntity<ReactionResponse> like(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(postService.like(id, userDetails.getId()));
    }

    @DeleteMapping("/{id}/like")
    public ResponseEntity<ReactionResponse> unlike(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(postService.unlike(id, userDetails.getId()));
    }

    @PostMapping("/{id}/bookmark")
    public ResponseEntity<ReactionResponse> bookmark(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(postService.bookmark(id, userDetails.getId()));
    }

    @DeleteMapping("/{id}/bookmark")
    public ResponseEntity<ReactionResponse> removeBookmark(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(postService.removeBookmark(id, userDetails.getId()));
    }

    @GetMapping("/{id}/exif")
    public ResponseEntity<PostExifResponse> getExif(@PathVariable Long id) {
        return ResponseEntity.ok(postService.getExif(id));
    }

    private Long userId(CustomUserDetails userDetails) {
        return userDetails == null ? null : userDetails.getId();
    }
}
