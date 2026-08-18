package com.project.picngo.user.service;

import com.project.picngo.admin.audit.domain.AdminActionType;
import com.project.picngo.admin.audit.service.AdminAuditLogService;
import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.UserErrorCode;
import com.project.picngo.user.domain.Role;
import com.project.picngo.user.domain.User;
import com.project.picngo.user.dto.AdminUserResponse;
import com.project.picngo.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserAdminService {

    private final UserRepository userRepository;
    private final AdminAuditLogService adminAuditLogService;

    /**
     * 관리자용 회원 목록 페이징 및 검색/필터링 조회
     */
    public Page<AdminUserResponse> getUsers(String keyword, Role role, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "createdAt"));
        String cleanKeyword = (keyword != null && !keyword.trim().isEmpty()) ? keyword.trim() : null;

        Page<User> users = userRepository.searchUsersForAdmin(cleanKeyword, role, pageable);
        return users.map(AdminUserResponse::from);
    }

    /**
     * 관리자용 회원 단건 상세 조회
     */
    public AdminUserResponse getUserDetail(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
        return AdminUserResponse.from(user);
    }

    /**
     * 관리자용 회원 권한(USER / ADMIN) 변경 (감사 로그 자동 기록)
     */
    @Transactional
    public AdminUserResponse updateUserRole(Long adminUserId, Long targetUserId, Role newRole) {
        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));

        Role oldRole = user.getRole();
        log.info("관리자에 의한 회원 권한 변경 요청: adminUserId={}, targetUserId={}, oldRole={}, newRole={}",
                adminUserId, targetUserId, oldRole, newRole);
        user.updateRole(newRole);

        // 관리자 감사 로그 기록
        try {
            adminAuditLogService.record(
                    adminUserId,
                    AdminActionType.ROLE_UPDATE,
                    "USER",
                    String.valueOf(targetUserId),
                    String.format("회원 [%s (ID: %d)]의 권한을 %s -> %s (으)로 변경", user.getNickname(), targetUserId, oldRole, newRole),
                    null
            );
        } catch (Exception e) {
            log.warn("회원 권한 변경 감사 로그 기록 실패: {}", e.getMessage());
        }

        return AdminUserResponse.from(user);
    }

    @Transactional
    public AdminUserResponse updateUserRole(Long targetUserId, Role newRole) {
        return updateUserRole(null, targetUserId, newRole);
    }
}
