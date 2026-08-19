package com.project.picngo.user.dto;

import com.project.picngo.user.domain.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "관리자용 회원 권한 변경 요청 DTO")
public record AdminUserRoleUpdateRequest(
        @Schema(description = "변경할 권한 (USER 또는 ADMIN)", example = "ADMIN")
        @NotNull(message = "변경할 권한(role)은 필수입니다.")
        Role role
) {
}
