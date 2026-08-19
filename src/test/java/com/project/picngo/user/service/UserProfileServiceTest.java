package com.project.picngo.user.service;

import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.UserErrorCode;
import com.project.picngo.user.domain.User;
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
import static org.mockito.Mockito.mock;
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
}
