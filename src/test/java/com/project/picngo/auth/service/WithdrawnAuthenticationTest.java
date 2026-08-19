package com.project.picngo.auth.service;

import com.project.picngo.user.domain.SocialProvider;
import com.project.picngo.user.domain.User;
import com.project.picngo.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * 탈퇴 계정은 인증 단계에서 끊는다.
 *
 * 서비스 계층(UserService.getById)만으로는 부족하다 — 글쓰기·댓글은 userRepository.findById를
 * 직접 쓰기 때문에 그 검사를 지나간다. 액세스 토큰이 1시간 유효하므로, 인증에서 막지 않으면
 * 탈퇴 직후에도 그 시간 동안 글을 쓸 수 있었다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WithdrawnAuthenticationTest {

    @Mock UserRepository userRepository;

    @InjectMocks CustomUserDetailsService service;

    private User user(boolean withdrawn) {
        User user = User.createSocialUser("a@kakao.local", "홍길동", null, SocialProvider.KAKAO, "1");
        if (withdrawn) {
            user.withdraw(LocalDateTime.now());
        }
        return user;
    }

    @Test
    @DisplayName("탈퇴 계정은 id로도 인증되지 않는다")
    void rejectsWithdrawnById() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(user(true)));

        assertThrows(UsernameNotFoundException.class, () -> service.loadUserById(7L));
    }

    @Test
    @DisplayName("탈퇴 계정은 이메일로도 인증되지 않는다")
    void rejectsWithdrawnByEmail() {
        when(userRepository.findByEmail("a@kakao.local")).thenReturn(Optional.of(user(true)));

        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername("a@kakao.local"));
    }

    @Test
    @DisplayName("정상 계정은 그대로 인증된다")
    void allowsActiveUser() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(user(false)));

        assertNotNull(service.loadUserById(7L));
    }
}
