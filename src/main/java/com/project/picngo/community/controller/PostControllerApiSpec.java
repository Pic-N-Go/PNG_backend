package com.project.picngo.community.controller;

import com.project.picngo.auth.service.CustomUserDetails;
import com.project.picngo.community.domain.PostSort;
import com.project.picngo.community.dto.PostCreateRequest;
import com.project.picngo.community.dto.PostExifResponse;
import com.project.picngo.community.dto.PostPageResponse;
import com.project.picngo.community.dto.PostResponse;
import com.project.picngo.community.dto.PostUpdateRequest;
import com.project.picngo.community.dto.ReactionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "커뮤니티 게시글 (Post)", description = "커뮤니티 게시글, 이미지, EXIF, 좋아요 및 북마크 API")
public interface PostControllerApiSpec {

    @Operation(
            summary = "게시글 목록 조회",
            description = """
                    게시글 목록을 조회합니다. 인증은 선택 사항이며, 인증된 경우 좋아요·북마크 여부가 함께 반환됩니다.
                    sort는 POPULAR(기본), LATEST, MY_POSTS, FOLLOWING을 지원합니다.
                    MY_POSTS는 로그인이 필요하고 FOLLOWING은 팔로우 기능 연동 전까지 사용할 수 없습니다.
                    keyword는 게시글 내용과 스팟 이름을 검색합니다.
                    """
    )
    ResponseEntity<PostPageResponse> getPosts(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "정렬 기준", example = "LATEST")
            @RequestParam(defaultValue = "POPULAR") PostSort sort,
            @Parameter(description = "게시글 내용 또는 스팟 이름 검색어", example = "한강")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "페이지 번호(0부터 시작)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기(1~100)", example = "20")
            @RequestParam(defaultValue = "20") int size
    );

    @Operation(
            summary = "게시글 작성",
            description = """
                    게시글 JSON과 이미지 파일을 multipart/form-data로 함께 전송합니다.
                    request 파트의 Content-Type은 application/json이어야 하며 이미지는 1~5장까지 등록할 수 있습니다.
                    파일당 최대 크기는 20MB이고 MIME 타입이 image/로 시작해야 합니다.
                    """,
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = @ApiResponse(responseCode = "201", description = "게시글 작성 성공")
    )
    ResponseEntity<PostResponse> createPost(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "게시글 작성 정보(JSON)")
            @Valid @RequestPart("request") PostCreateRequest request,
            @Parameter(description = "게시글 이미지 목록(1~5장)")
            @RequestPart("images") List<MultipartFile> images
    );

    @Operation(
            summary = "게시글 수정",
            description = """
                    게시글 작성자만 수정할 수 있습니다.
                    request에서 생략한 필드는 기존 값을 유지합니다.
                    retainedImageIds를 생략하면 기존 이미지를 모두 유지하고, 전달하면 해당 ID와 순서대로 유지합니다.
                    빈 배열을 전달하면 기존 이미지를 모두 제거하므로 newImages를 최소 한 장 함께 전송해야 합니다.
                    수정 후 최종 이미지 개수는 1~5장이어야 합니다.
                    """,
            security = @SecurityRequirement(name = "bearerAuth")
    )
    ResponseEntity<PostResponse> updatePost(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "게시글 ID", example = "4") @PathVariable Long id,
            @Parameter(description = "게시글 수정 정보(JSON)")
            @Valid @RequestPart("request") PostUpdateRequest request,
            @Parameter(description = "새로 추가할 이미지 목록")
            @RequestPart(value = "newImages", required = false) List<MultipartFile> newImages
    );

    @Operation(
            summary = "게시글 삭제",
            description = "게시글 작성자만 삭제할 수 있으며 댓글, 좋아요, 북마크, 이미지와 S3 객체를 함께 정리합니다.",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = @ApiResponse(responseCode = "204", description = "게시글 삭제 성공")
    )
    ResponseEntity<Void> deletePost(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "게시글 ID", example = "4") @PathVariable Long id
    );

    @Operation(
            summary = "게시글 상세 조회",
            description = "게시글 상세 정보를 조회합니다. 인증된 경우 좋아요·북마크 여부가 함께 반환됩니다."
    )
    ResponseEntity<PostResponse> getPost(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "게시글 ID", example = "4") @PathVariable Long id
    );

    @Operation(
            summary = "게시글 좋아요",
            description = "게시글에 좋아요를 등록합니다. 이미 좋아요한 경우에도 중복 저장하지 않습니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    ResponseEntity<ReactionResponse> like(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "게시글 ID", example = "4") @PathVariable Long id
    );

    @Operation(
            summary = "게시글 좋아요 취소",
            description = "게시글의 좋아요를 취소합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    ResponseEntity<ReactionResponse> unlike(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "게시글 ID", example = "4") @PathVariable Long id
    );

    @Operation(
            summary = "게시글 북마크",
            description = "게시글을 북마크합니다. 이미 북마크한 경우에도 중복 저장하지 않습니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    ResponseEntity<ReactionResponse> bookmark(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "게시글 ID", example = "4") @PathVariable Long id
    );

    @Operation(
            summary = "게시글 북마크 취소",
            description = "게시글의 북마크를 취소합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    ResponseEntity<ReactionResponse> removeBookmark(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "게시글 ID", example = "4") @PathVariable Long id
    );

    @Operation(
            summary = "게시글 이미지 EXIF 조회",
            description = "게시글 이미지의 카메라·렌즈·촬영 설정·GPS·파일 정보를 업로드 순서대로 반환합니다."
    )
    ResponseEntity<PostExifResponse> getExif(
            @Parameter(description = "게시글 ID", example = "4") @PathVariable Long id
    );
}
