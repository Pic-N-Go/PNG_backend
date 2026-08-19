package com.project.picngo.user.service;

import com.project.picngo.common.image.dto.ImageUploadResult;
import com.project.picngo.common.image.service.ImageStorageService;
import com.project.picngo.user.domain.SocialProvider;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 프로필 사진은 DB에 objectKey로 저장되고 응답에서만 presigned URL이 된다.
 * 카카오가 준 http URL과 우리가 올린 key가 같은 컬럼에 섞이므로, 어느 쪽인지에 따라
 * 삭제 대상과 카카오 동기화 여부가 갈린다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProfileImageTest {

    @Mock UserRepository userRepository;
    @Mock FollowRepository followRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock ImageStorageService imageStorageService;

    @InjectMocks UserService service;

    private final MockMultipartFile file =
            new MockMultipartFile("image", "me.jpg", "image/jpeg", new byte[] {1, 2, 3});

    private User userWith(String storedProfileImage) {
        User user = User.createSocialUser(
                "a@kakao.local", "홍길동", storedProfileImage, SocialProvider.KAKAO, "1");
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        return user;
    }

    @Test
    @DisplayName("업로드하면 URL이 아니라 objectKey를 저장하고, 응답에는 presigned URL을 담는다")
    void storesKeyAndReturnsPresignedUrl() {
        User user = userWith(null);
        when(imageStorageService.upload(any(), anyString()))
                .thenReturn(new ImageUploadResult("profile/7/abc.jpg", "https://s3/presigned"));

        var response = service.updateProfileImage(7L, file);

        assertEquals("profile/7/abc.jpg", user.getProfileImageUrl());
        assertEquals("https://s3/presigned", response.profileImageUrl());
    }

    @Test
    @DisplayName("직접 올렸던 사진은 교체 시 저장소에서 지운다")
    void deletesPreviousUploadedImage() {
        userWith("profile/7/old.jpg");
        when(imageStorageService.upload(any(), anyString()))
                .thenReturn(new ImageUploadResult("profile/7/new.jpg", "https://s3/presigned"));

        service.updateProfileImage(7L, file);

        verify(imageStorageService).delete("profile/7/old.jpg");
    }

    @Test
    @DisplayName("카카오가 준 사진은 우리 소유가 아니라 지우지 않는다")
    void doesNotDeleteKakaoImage() {
        userWith("https://k.kakaocdn.net/profile.jpg");
        when(imageStorageService.upload(any(), anyString()))
                .thenReturn(new ImageUploadResult("profile/7/new.jpg", "https://s3/presigned"));

        service.updateProfileImage(7L, file);

        verify(imageStorageService, never()).delete(anyString());
    }

    @Test
    @DisplayName("삭제하면 값을 비우고 올렸던 파일도 지운다")
    void clearsProfileImage() {
        User user = userWith("profile/7/old.jpg");

        var response = service.deleteProfileImage(7L);

        assertNull(user.getProfileImageUrl());
        assertNull(response.profileImageUrl());
        verify(imageStorageService).delete("profile/7/old.jpg");
    }

    @Test
    @DisplayName("직접 올린 사진은 카카오 재로그인에 덮이지 않는다")
    void keepsUploadedImageOnKakaoRelogin() {
        User user = User.createSocialUser(
                "a@kakao.local", "홍길동", "profile/7/mine.jpg", SocialProvider.KAKAO, "1");
        assertTrue(user.hasUploadedProfileImage());

        user.updateSocialProfile("https://k.kakaocdn.net/changed.jpg");

        assertEquals("profile/7/mine.jpg", user.getProfileImageUrl());
    }

    @Test
    @DisplayName("카카오 사진만 있던 계정은 재로그인 때 최신 카카오 사진으로 갱신된다")
    void stillSyncsKakaoImageWhenNothingUploaded() {
        User user = User.createSocialUser(
                "a@kakao.local", "홍길동", "https://k.kakaocdn.net/old.jpg", SocialProvider.KAKAO, "1");
        assertFalse(user.hasUploadedProfileImage());

        user.updateSocialProfile("https://k.kakaocdn.net/new.jpg");

        assertEquals("https://k.kakaocdn.net/new.jpg", user.getProfileImageUrl());
    }
}
