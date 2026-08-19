package com.project.picngo.admin.audit.service;

import com.project.picngo.admin.audit.domain.AdminActionType;
import com.project.picngo.admin.audit.domain.AdminAuditLog;
import com.project.picngo.admin.audit.dto.AdminAuditLogResponse;
import com.project.picngo.admin.audit.repository.AdminAuditLogRepository;
import com.project.picngo.user.domain.Role;
import com.project.picngo.user.domain.SocialProvider;
import com.project.picngo.user.domain.User;
import com.project.picngo.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminAuditLogServiceTest {

    @Mock
    private AdminAuditLogRepository adminAuditLogRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AdminAuditLogService adminAuditLogService;

    @Test
    @DisplayName("관리자 액션 감사 로그 기록 시 관리자 정보(이메일, 닉네임)를 스냅샷하여 저장한다")
    void record_snapshots_admin_details() {
        // given
        User admin = User.builder()
                .email("admin@picngo.com")
                .nickname("슈퍼관리자")
                .role(Role.ADMIN)
                .provider(SocialProvider.LOCAL)
                .providerId("admin")
                .build();
        ReflectionTestUtils.setField(admin, "id", 1L);

        given(userRepository.findById(1L)).willReturn(Optional.of(admin));

        // when
        adminAuditLogService.record(
                1L,
                AdminActionType.ROLE_UPDATE,
                "USER",
                "10",
                "회원 권한 변경 (USER -> ADMIN)",
                "127.0.0.1"
        );

        // then
        ArgumentCaptor<AdminAuditLog> captor = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(adminAuditLogRepository).save(captor.capture());

        AdminAuditLog savedLog = captor.getValue();
        assertThat(savedLog.getAdminUserId()).isEqualTo(1L);
        assertThat(savedLog.getAdminEmail()).isEqualTo("admin@picngo.com");
        assertThat(savedLog.getAdminNickname()).isEqualTo("슈퍼관리자");
        assertThat(savedLog.getActionType()).isEqualTo(AdminActionType.ROLE_UPDATE);
        assertThat(savedLog.getTargetEntity()).isEqualTo("USER");
        assertThat(savedLog.getTargetId()).isEqualTo("10");
        assertThat(savedLog.getDetails()).contains("회원 권한 변경");
        assertThat(savedLog.getIpAddress()).isEqualTo("127.0.0.1");
    }

    @Test
    @DisplayName("감사 로그 목록을 페이징 조회할 수 있다")
    void getAuditLogs_paging() {
        // given
        AdminAuditLog log1 = AdminAuditLog.create(1L, "admin@picngo.com", "관리자", AdminActionType.ROLE_UPDATE, "USER", "10", "내용", "127.0.0.1");
        ReflectionTestUtils.setField(log1, "id", 1L);

        Page<AdminAuditLog> page = new PageImpl<>(List.of(log1));
        given(adminAuditLogRepository.searchLogs(eq(1L), eq(AdminActionType.ROLE_UPDATE), any(Pageable.class)))
                .willReturn(page);

        // when
        Page<AdminAuditLogResponse> result = adminAuditLogService.getAuditLogs(1L, AdminActionType.ROLE_UPDATE, 0, 20);

        // then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).actionDescription()).isEqualTo("회원 권한 변경");
    }
}
