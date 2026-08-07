package com.project.picngo.album.controller;

import com.project.picngo.album.dto.AlbumCreateRequest;
import com.project.picngo.album.dto.AlbumDetailResponse;
import com.project.picngo.album.dto.AlbumResponse;
import com.project.picngo.album.dto.AlbumUpdateRequest;
import com.project.picngo.album.service.AlbumService;
import com.project.picngo.auth.service.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/users/me/albums")
public class AlbumController implements AlbumControllerApiSpec {
    private final AlbumService albumService;

    // 내 앨범 목록 조회 API
    @GetMapping
    public ResponseEntity<List<AlbumResponse>> getMyAlbums(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(albumService.getMyAlbums(userDetails.getId()));
    }

    // 앨범 상세 조회 API
    @GetMapping("/{albumId}")
    public ResponseEntity<AlbumDetailResponse> getAlbumDetail(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long albumId
    ) {
        return ResponseEntity.ok(albumService.getAlbumDetail(userDetails.getId(), albumId));
    }

    // 앨범 생성 API
    @PostMapping
    public ResponseEntity<AlbumResponse> createAlbum(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody AlbumCreateRequest request
    ) {
        return ResponseEntity.ok(albumService.createAlbum(userDetails.getId(), request));
    }

    // 앨범 수정 API
    @PutMapping("/{albumId}")
    public ResponseEntity<AlbumResponse> updateAlbum(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long albumId,
            @RequestBody AlbumUpdateRequest request
    ) {
        return ResponseEntity.ok(albumService.updateAlbum(userDetails.getId(), albumId, request));
    }

    // 앨범 삭제 API
    @DeleteMapping("/{albumId}")
    public ResponseEntity<Void> deleteAlbum(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long albumId
    ) {
        albumService.deleteAlbum(userDetails.getId(), albumId);
        return ResponseEntity.ok().build();
    }

    // 앨범 사진 추가 API
    @PostMapping(value = "/{albumId}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AlbumDetailResponse> addAlbumPhoto(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long albumId,
            @RequestPart("photos") List<MultipartFile> photos
    ) {
        return ResponseEntity.ok(albumService.addAlbumPhoto(userDetails.getId(), albumId, photos));
    }

    // 앨범 사진 삭제 API
    @DeleteMapping("/{albumId}/photos/{photoId}")
    public ResponseEntity<Void> deleteAlbumPhoto(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long albumId,
            @PathVariable Long photoId
    ) {
        albumService.deleteAlbumPhoto(userDetails.getId(), albumId, photoId);
        return ResponseEntity.ok().build();
    }

}
