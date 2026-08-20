package com.project.picngo.user.controller;

import com.project.picngo.auth.service.CustomUserDetails;
import com.project.picngo.user.domain.Role;
import com.project.picngo.user.dto.AdminUserResponse;
import com.project.picngo.user.dto.AdminUserRoleUpdateRequest;
import com.project.picngo.user.service.UserAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 관리자용 회원 및 권한 관리 컨트롤러.
 * SecurityConfig 규칙에 따라 /admin/** 하위 경로는 ROLE_ADMIN 권한이 필수입니다.
 */
@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class UserAdminController implements UserAdminControllerApiSpec {

    private final UserAdminService userAdminService;

    @Override
    @GetMapping
    public ResponseEntity<Page<AdminUserResponse>> getUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Role role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(userAdminService.getUsers(keyword, role, page, size));
    }

    @Override
    @GetMapping("/{userId}")
    public ResponseEntity<AdminUserResponse> getUserDetail(@PathVariable Long userId) {
        return ResponseEntity.ok(userAdminService.getUserDetail(userId));
    }

    @Override
    @PatchMapping("/{userId}/role")
    public ResponseEntity<AdminUserResponse> updateUserRole(
            @AuthenticationPrincipal CustomUserDetails adminUserDetails,
            @PathVariable Long userId,
            @Valid @RequestBody AdminUserRoleUpdateRequest request
    ) {
        Long adminId = adminUserDetails != null ? adminUserDetails.getId() : null;
        return ResponseEntity.ok(userAdminService.updateUserRole(adminId, userId, request.role()));
    }
}
