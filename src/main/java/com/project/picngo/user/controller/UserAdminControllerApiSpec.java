package com.project.picngo.user.controller;

import com.project.picngo.user.domain.Role;
import com.project.picngo.user.dto.AdminUserResponse;
import com.project.picngo.user.dto.AdminUserRoleUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 관리자용 회원 및 권한 관리 API 명세 (Swagger Spec).
 * SecurityConfig 규칙에 따라 ROLE_ADMIN 권한이 필요합니다.
 */
@Tag(name = "관리자 - 회원", description = "관리자 전용 회원 목록 조회, 검색 및 권한(USER/ADMIN) 관리 API")
@SecurityRequirement(name = "bearerAuth")
public interface UserAdminControllerApiSpec {

    @Operation(
            summary = "관리자용 회원 목록 및 검색 조회",
            description = "전체 회원 목록을 최신 가입순으로 페이징 조회합니다. 이메일/닉네임 키워드 검색 및 권한(USER/ADMIN) 필터링이 가능합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "회원 목록 조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content),
            @ApiResponse(responseCode = "403", description = "권한 부족 (ADMIN 권한 없음)", content = @Content)
    })
    ResponseEntity<Page<AdminUserResponse>> getUsers(
            @Parameter(description = "이메일 또는 닉네임 검색 키워드", example = "test") @RequestParam(required = false) String keyword,
            @Parameter(description = "권한 필터 (USER 또는 ADMIN)", example = "USER") @RequestParam(required = false) Role role,
            @Parameter(description = "페이지 번호 (0부터 시작)", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기", example = "20") @RequestParam(defaultValue = "20") int size
    );

    @Operation(
            summary = "관리자용 회원 단건 상세 조회",
            description = "특정 회원의 상세 정보(이메일, 닉네임, 권한, 가입 소셜 제공자 등)를 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "회원 상세 정보 조회 성공",
                    content = @Content(schema = @Schema(implementation = AdminUserResponse.class))
            ),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content),
            @ApiResponse(responseCode = "403", description = "권한 부족 (ADMIN 권한 없음)", content = @Content),
            @ApiResponse(responseCode = "404", description = "회원을 찾을 수 없음", content = @Content)
    })
    ResponseEntity<AdminUserResponse> getUserDetail(
            @Parameter(description = "조회할 회원 ID", example = "1") @PathVariable Long userId
    );

    @Operation(
            summary = "관리자용 회원 권한 변경",
            description = "특정 회원의 권한을 변경(USER ↔ ADMIN)합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "회원 권한 변경 성공",
                    content = @Content(schema = @Schema(implementation = AdminUserResponse.class))
            ),
            @ApiResponse(responseCode = "400", description = "유효하지 않은 요청 데이터", content = @Content),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content),
            @ApiResponse(responseCode = "403", description = "권한 부족 (ADMIN 권한 없음)", content = @Content),
            @ApiResponse(responseCode = "404", description = "회원을 찾을 수 없음", content = @Content)
    })
    ResponseEntity<AdminUserResponse> updateUserRole(
            @Parameter(hidden = true) com.project.picngo.auth.service.CustomUserDetails adminUserDetails,
            @Parameter(description = "권한을 변경할 회원 ID", example = "1") @PathVariable Long userId,
            @Valid @RequestBody AdminUserRoleUpdateRequest request
    );
}
