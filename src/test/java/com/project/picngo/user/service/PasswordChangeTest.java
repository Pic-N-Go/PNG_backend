package com.project.picngo.user.service;

import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.AuthErrorCode;
import com.project.picngo.user.domain.SocialProvider;
import com.project.picngo.user.domain.User;
import com.project.picngo.user.dto.PasswordChangeRequest;
import com.project.picngo.user.repository.FollowRepository;
import com.project.picngo.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 설정 > 비밀번호 변경. 소셜 계정은 비밀번호가 없어(password null) 이 경로를 쓸 수 없다 —
 * 막지 않으면 matches(raw, null)이 터지거나 소셜 계정에 비밀번호가 생겨
 * 의도한 적 없는 이메일 로그인 진입점이 열린다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PasswordChangeTest {

    @Mock UserRepository userRepository;
    @Mock FollowRepository followRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock com.project.picngo.auth.service.RefreshTokenService refreshTokenService;

    @InjectMocks UserService service;

    private User localUser(String encodedPassword) {
        User user = mock(User.class);
        when(user.getProvider()).thenReturn(SocialProvider.LOCAL);
        when(user.getPassword()).thenReturn(encodedPassword);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        return user;
    }

    @Test
    @DisplayName("현재 비밀번호가 맞으면 새 비밀번호로 바꾼다")
    void changesPassword() {
        User user = localUser("encoded-old");
        when(passwordEncoder.matches("old-password", "encoded-old")).thenReturn(true);
        when(passwordEncoder.encode("new-password")).thenReturn("encoded-new");

        service.changePassword(7L, new PasswordChangeRequest("old-password", "new-password"));

        verify(user).updatePassword("encoded-new");
        // 이전 비밀번호로 만들어진 세션이 살아 있으면 비밀번호를 바꾼 의미가 반감된다.
        verify(refreshTokenService).revokeAllByUserId(7L);
    }

    @Test
    @DisplayName("현재 비밀번호가 틀리면 INVALID_CURRENT_PASSWORD를 반환한다")
    void rejectsWrongCurrentPassword() {
        User user = localUser("encoded-old");
        when(passwordEncoder.matches("wrong", "encoded-old")).thenReturn(false);

        CustomException exception = assertThrows(
                CustomException.class,
                () -> service.changePassword(7L, new PasswordChangeRequest("wrong", "new-password"))
        );

        assertEquals(AuthErrorCode.INVALID_CURRENT_PASSWORD, exception.getErrorCode());
        verify(user, never()).updatePassword(anyString());
        verify(refreshTokenService, never()).revokeAllByUserId(anyLong());
    }

    @Test
    @DisplayName("소셜 계정은 비밀번호를 바꿀 수 없다")
    void rejectsSocialAccount() {
        User user = mock(User.class);
        when(user.getProvider()).thenReturn(SocialProvider.KAKAO);
        when(user.getPassword()).thenReturn(null);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));

        CustomException exception = assertThrows(
                CustomException.class,
                () -> service.changePassword(7L, new PasswordChangeRequest("anything", "new-password"))
        );

        assertEquals(AuthErrorCode.SOCIAL_ACCOUNT_HAS_NO_PASSWORD, exception.getErrorCode());
        // 비밀번호 대조 자체를 시도하면 안 된다 — matches(raw, null)은 구현에 따라 터진다
        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(user, never()).updatePassword(anyString());
    }

    @Test
    @DisplayName("LOCAL이어도 비밀번호가 비어 있으면 거부한다")
    void rejectsLocalAccountWithoutPassword() {
        localUser(null);

        CustomException exception = assertThrows(
                CustomException.class,
                () -> service.changePassword(7L, new PasswordChangeRequest("anything", "new-password"))
        );

        assertEquals(AuthErrorCode.SOCIAL_ACCOUNT_HAS_NO_PASSWORD, exception.getErrorCode());
    }
}
