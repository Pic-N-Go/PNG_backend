package com.project.picngo.album.controller;

import com.project.picngo.album.dto.AlbumCreateRequest;
import com.project.picngo.album.dto.AlbumDetailResponse;
import com.project.picngo.album.dto.AlbumPhotoAddRequest;
import com.project.picngo.album.dto.AlbumResponse;
import com.project.picngo.album.dto.AlbumUpdateRequest;
import com.project.picngo.auth.service.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@Tag(name = "앨범 (Album)", description = "사용자 앨범 관리 API")
public interface AlbumControllerApiSpec {

    @Operation(
            summary = "내 앨범 목록 조회",
            description = "현재 인증된 사용자의 앨범 목록을 조회합니다."
    )
    ResponseEntity<List<AlbumResponse>> getMyAlbums(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails
    );

    @Operation(
            summary = "앨범 상세 조회",
            description = "현재 인증된 사용자의 특정 앨범 상세 정보를 조회합니다."
    )
    ResponseEntity<AlbumDetailResponse> getAlbumDetail(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "앨범 ID") @PathVariable Long albumId
    );

    @Operation(
            summary = "앨범 생성",
            description = "현재 인증된 사용자의 앨범을 생성합니다."
    )
    ResponseEntity<AlbumResponse> createAlbum(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody AlbumCreateRequest request
    );

    @Operation(
            summary = "앨범 수정",
            description = "현재 인증된 사용자의 앨범 이름, 카테고리, 공개 여부를 수정합니다."
    )
    ResponseEntity<AlbumResponse> updateAlbum(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "앨범 ID") @PathVariable Long albumId,
            @RequestBody AlbumUpdateRequest request
    );

    @Operation(
            summary = "앨범 삭제",
            description = "현재 인증된 사용자의 앨범을 삭제합니다."
    )
    ResponseEntity<Void> deleteAlbum(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "앨범 ID") @PathVariable Long albumId
    );

    @Operation(
            summary = "앨범 사진 추가",
            description = "현재 인증된 사용자의 앨범에 사진 URL을 추가합니다."
    )
    ResponseEntity<AlbumDetailResponse> addAlbumPhoto(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "앨범 ID") @PathVariable Long albumId,
            @RequestBody AlbumPhotoAddRequest request
    );

    @Operation(
            summary = "앨범 사진 삭제",
            description = "현재 인증된 사용자의 앨범 사진을 삭제합니다."
    )
    ResponseEntity<Void> deleteAlbumPhoto(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "앨범 ID") @PathVariable Long albumId,
            @Parameter(description = "사진 ID") @PathVariable Long photoId
    );
}
