package com.project.picngo.user.service;

import com.project.picngo.admin.audit.domain.AdminActionType;
import com.project.picngo.admin.audit.service.AdminAuditLogService;
import com.project.picngo.user.domain.Role;
import com.project.picngo.user.domain.SocialProvider;
import com.project.picngo.user.domain.User;
import com.project.picngo.user.dto.AdminUserResponse;
import com.project.picngo.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserAdminServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AdminAuditLogService adminAuditLogService;

    @InjectMocks
    private UserAdminService userAdminService;

    @Test
    @DisplayName("관리자가 회원의 권한을 변경하면 권한이 업데이트되고 감사 로그가 기록된다")
    void updateUserRole_success_and_records_audit_log() {
        // given
        User targetUser = User.builder()
                .email("user@example.com")
                .nickname("일반회원")
                .role(Role.USER)
                .provider(SocialProvider.LOCAL)
                .providerId("user1")
                .build();
        ReflectionTestUtils.setField(targetUser, "id", 10L);

        given(userRepository.findById(10L)).willReturn(Optional.of(targetUser));

        // when
        AdminUserResponse response = userAdminService.updateUserRole(1L, 10L, Role.ADMIN);

        // then
        assertThat(response.role()).isEqualTo(Role.ADMIN);
        assertThat(targetUser.getRole()).isEqualTo(Role.ADMIN);

        // 관리자 감사 로그 기록 검증
        verify(adminAuditLogService).record(
                eq(1L),
                eq(AdminActionType.ROLE_UPDATE),
                eq("USER"),
                eq("10"),
                contains("USER -> ADMIN"),
                isNull()
        );
    }
}
