package com.project.picngo.user.service;

import com.project.picngo.common.image.dto.ImageUploadResult;
import com.project.picngo.common.image.service.ImageStorageService;
import com.project.picngo.user.domain.SocialProvider;
import com.project.picngo.user.domain.User;
import com.project.picngo.user.repository.FollowRepository;
import com.project.picngo.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 프로필 사진은 두 칸으로 나뉜다 — 사용자가 올린 것(S3 objectKey)과 소셜에서 받은 것(URL).
 * 표시값은 "올린 것 ?? 소셜 것"이라, 올린 사진을 지우면 카카오 사진으로 되돌아간다.
 * 한 칸에 섞어 쓰면 사진을 올리는 순간 카카오 URL이 덮여 되돌릴 원본이 없어진다.
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

    @BeforeEach
    void passThroughPresign() {
        // presign은 이 테스트의 관심사가 아니다 — 값이 그대로 흘러가는지만 본다.
        when(imageStorageService.getPresignedUrl(anyString())).thenAnswer(i -> i.getArgument(0));
    }

    /** 카카오로 가입한 사용자. 소셜 사진은 소셜 칸에 들어간다. */
    private User kakaoUser(String socialImageUrl) {
        User user = User.createSocialUser(
                "a@kakao.local", "홍길동", socialImageUrl, SocialProvider.KAKAO, "1");
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        return user;
    }

    @Test
    @DisplayName("업로드하면 URL이 아니라 objectKey를 저장하고, 응답에는 presigned URL을 담는다")
    void storesKeyAndReturnsPresignedUrl() {
        User user = kakaoUser(null);
        when(imageStorageService.upload(any(), anyString()))
                .thenReturn(new ImageUploadResult("profile/7/abc.jpg", "https://s3/presigned"));

        var response = service.updateProfileImage(7L, file);

        assertEquals("profile/7/abc.jpg", user.getProfileImageUrl());
        assertEquals("https://s3/presigned", response.profileImageUrl());
    }

    @Test
    @DisplayName("올린 사진은 소셜 사진보다 우선해서 보인다")
    void uploadedImageWinsOverSocial() {
        User user = kakaoUser("https://k.kakaocdn.net/profile.jpg");
        when(imageStorageService.upload(any(), anyString()))
                .thenReturn(new ImageUploadResult("profile/7/mine.jpg", "https://s3/presigned"));

        service.updateProfileImage(7L, file);

        assertEquals("profile/7/mine.jpg", user.getDisplayProfileImage());
    }

    @Test
    @DisplayName("직접 올렸던 사진은 교체 시 저장소에서 지운다")
    void deletesPreviousUploadedImage() {
        User user = kakaoUser(null);
        user.updateProfileImage("profile/7/old.jpg");
        when(imageStorageService.upload(any(), anyString()))
                .thenReturn(new ImageUploadResult("profile/7/new.jpg", "https://s3/presigned"));

        service.updateProfileImage(7L, file);

        verify(imageStorageService).delete("profile/7/old.jpg");
    }

    @Test
    @DisplayName("소셜 사진은 우리 소유가 아니라 지우지 않는다")
    void doesNotDeleteSocialImage() {
        kakaoUser("https://k.kakaocdn.net/profile.jpg");
        when(imageStorageService.upload(any(), anyString()))
                .thenReturn(new ImageUploadResult("profile/7/new.jpg", "https://s3/presigned"));

        service.updateProfileImage(7L, file);

        verify(imageStorageService, never()).delete(anyString());
    }

    @Test
    @DisplayName("올린 사진을 지우면 카카오 사진으로 되돌아간다")
    void fallsBackToSocialImageOnDelete() {
        User user = kakaoUser("https://k.kakaocdn.net/profile.jpg");
        user.updateProfileImage("profile/7/mine.jpg");

        var response = service.deleteProfileImage(7L);

        assertNull(user.getProfileImageUrl());
        assertEquals("https://k.kakaocdn.net/profile.jpg", response.profileImageUrl());
        verify(imageStorageService).delete("profile/7/mine.jpg");
    }

    @Test
    @DisplayName("소셜 사진이 없으면 지운 뒤 사진 없음이 된다")
    void clearsImageWhenNoSocialFallback() {
        User user = kakaoUser(null);
        user.updateProfileImage("profile/7/mine.jpg");

        var response = service.deleteProfileImage(7L);

        assertNull(user.getDisplayProfileImage());
        assertNull(response.profileImageUrl());
    }

    @Test
    @DisplayName("재로그인은 소셜 칸만 갱신한다 — 올린 사진은 그대로 보인다")
    void reloginUpdatesOnlySocialSlot() {
        User user = kakaoUser("https://k.kakaocdn.net/old.jpg");
        user.updateProfileImage("profile/7/mine.jpg");

        user.updateSocialProfile("https://k.kakaocdn.net/new.jpg");

        assertEquals("profile/7/mine.jpg", user.getDisplayProfileImage());
        // 갱신은 됐다 — 나중에 올린 사진을 지우면 최신 카카오 사진으로 떨어진다.
        user.updateProfileImage(null);
        assertEquals("https://k.kakaocdn.net/new.jpg", user.getDisplayProfileImage());
    }
}
