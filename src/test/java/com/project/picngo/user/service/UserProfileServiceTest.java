package com.project.picngo.user.service;

import com.project.picngo.common.image.service.ImageStorageService;
import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.UserErrorCode;
import com.project.picngo.user.domain.User;
import com.project.picngo.user.dto.UserProfileUpdateRequest;
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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 프로필 응답에 팔로워·팔로잉 수가 실려 나가는지 확인한다.
 * 이 값이 빠지면 클라이언트가 다시 팔로워 목록 전체를 받아 세는 방식으로 되돌아간다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserProfileServiceTest {

    @Mock UserRepository userRepository;
    @Mock FollowRepository followRepository;
    @Mock ImageStorageService imageStorageService;

    @InjectMocks UserService service;

    @Test
    @DisplayName("프로필 조회 시 팔로워·팔로잉 수를 함께 반환한다")
    void profileIncludesFollowCounts() {
        User user = mock(User.class);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(followRepository.countByFollowing(user)).thenReturn(12L);
        when(followRepository.countByFollower(user)).thenReturn(34L);

        var response = service.getUserProfile(7L);

        assertEquals(12L, response.followerCount());
        assertEquals(34L, response.followingCount());
    }

    @Test
    @DisplayName("없는 사용자의 프로필을 조회하면 USER_NOT_FOUND를 반환한다")
    void missingUserProfileIsRejected() {
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        CustomException exception = assertThrows(
                CustomException.class,
                () -> service.getUserProfile(404L)
        );

        assertEquals(UserErrorCode.USER_NOT_FOUND, exception.getErrorCode());
    }

    /**
     * 닉네임 형식 검증은 요청 DTO(@Pattern)가 아니라 여기서 한다. DTO에 걸면 새 규칙 이전에
     * 만들어진 닉네임을 가진 계정이 자기소개만 고치려 해도 400이 난다 — PUT은 전체 교체라
     * 클라이언트가 현재 닉네임을 그대로 되돌려 보내기 때문이다.
     */
    @Test
    @DisplayName("닉네임을 그대로 두면 규칙 위반 값이어도 자기소개를 수정할 수 있다")
    void keepsInvalidNicknameWhenUnchanged() {
        User user = mock(User.class);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        // 새 규칙 이전에 카카오에서 들어온 닉네임(특수문자 포함, 규칙 위반)
        when(user.getNickname()).thenReturn("홍길동님♥");

        service.updateMyProfile(7L, new UserProfileUpdateRequest("홍길동님♥", "안녕하세요"));

        verify(user).updateProfile("홍길동님♥", "안녕하세요");
        // 안 바꿨으므로 중복 조회조차 하지 않는다
        verify(userRepository, never()).existsByNicknameAndIdNot(anyString(), any());
    }

    @Test
    @DisplayName("닉네임을 규칙에 맞지 않는 값으로 바꾸면 INVALID_NICKNAME을 반환한다")
    void rejectsInvalidNewNickname() {
        User user = mock(User.class);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(user.getNickname()).thenReturn("홍길동");

        CustomException exception = assertThrows(
                CustomException.class,
                () -> service.updateMyProfile(7L, new UserProfileUpdateRequest("홍길동!!", null))
        );

        assertEquals(UserErrorCode.INVALID_NICKNAME, exception.getErrorCode());
        verify(user, never()).updateProfile(anyString(), any());
    }

    @Test
    @DisplayName("이미 쓰이는 닉네임으로 바꾸면 NICKNAME_ALREADY_EXISTS를 반환한다")
    void rejectsDuplicateNewNickname() {
        User user = mock(User.class);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(user.getNickname()).thenReturn("홍길동");
        when(userRepository.existsByNicknameAndIdNot("김지우", 7L)).thenReturn(true);

        CustomException exception = assertThrows(
                CustomException.class,
                () -> service.updateMyProfile(7L, new UserProfileUpdateRequest("김지우", null))
        );

        assertEquals(UserErrorCode.NICKNAME_ALREADY_EXISTS, exception.getErrorCode());
    }
}
