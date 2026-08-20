package com.project.picngo.bookmark.controller;

import com.project.picngo.auth.service.CustomUserDetails;
import com.project.picngo.bookmark.dto.BookmarkCollectionResponse;
import com.project.picngo.bookmark.dto.CreateCollectionRequest;
import com.project.picngo.bookmark.dto.SyncCollectionsRequest;
import com.project.picngo.spot.dto.SpotResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Tag(name = "북마크 컬렉션", description = "스팟 북마크(컬렉션) API")
public interface BookmarkCollectionControllerApiSpec {

    @Operation(
            summary = "컬렉션 목록 조회",
            description = "유저의 북마크 컬렉션 목록을 반환합니다. spotId를 주면 각 컬렉션에 해당 스팟 소속 여부(contains)를 포함합니다. 최초 조회 시 기본 컬렉션('내 즐겨찾기')이 자동 생성됩니다. 로그인 필요.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    ResponseEntity<List<BookmarkCollectionResponse>> getCollections(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "소속 여부를 확인할 스팟 ID (선택)") @RequestParam(required = false) Long spotId
    );

    @Operation(
            summary = "북마크한 스팟 전체 조회",
            description = "컬렉션 구분 없이 담아둔 스팟을 최근 담은 순으로 반환합니다. 여러 컬렉션에 담긴 스팟은 한 번만 나옵니다. "
                    + "응답의 isBookmarked는 항상 true입니다. 로그인 필요.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    ResponseEntity<List<SpotResponse>> getBookmarkedSpots(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails
    );

    @Operation(
            summary = "컬렉션에 담긴 스팟 목록 조회",
            description = "컬렉션에 담긴 스팟을 최근 담은 순으로 반환합니다. 응답의 isBookmarked는 항상 true입니다. "
                    + "본인 컬렉션이 아니거나 없으면 404. 로그인 필요.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    ResponseEntity<List<SpotResponse>> getCollectionSpots(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "컬렉션 ID") @PathVariable Long collectionId
    );

    @Operation(
            summary = "컬렉션 생성",
            description = "이름 + color + icon으로 컬렉션을 생성합니다. 유저당 최대 5개, 이름 20자 이하. color/icon은 허용 키만 가능합니다. 로그인 필요.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    ResponseEntity<BookmarkCollectionResponse> createCollection(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CreateCollectionRequest request
    );

    @Operation(
            summary = "스팟 소속 컬렉션 동기화",
            description = "체크된 collectionIds 집합으로 이 스팟의 멤버십을 통째 동기화합니다(빠진 컬렉션은 제거). 빈 배열이면 모든 컬렉션에서 제거됩니다. 로그인 필요.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    ResponseEntity<Void> syncSpotCollections(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "스팟 ID") @PathVariable Long spotId,
            @Valid @RequestBody SyncCollectionsRequest request
    );
}
