package com.project.picngo.user.service;

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

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 카카오 닉네임은 우리 규칙(2~10자, 한글/영문/숫자)을 지키지 않고 유일하지도 않다.
 * 요청 DTO를 거치지 않는 경로라 @Pattern이 안 걸리므로, 여기서 막히지 않으면
 * 규칙 위반·중복 닉네임이 그대로 DB에 남는다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SocialNicknameTest {

    @Mock UserRepository userRepository;
    @Mock FollowRepository followRepository;

    @InjectMocks UserService service;

    /** 저장 직전 닉네임을 꺼내 본다 — 실제로 DB로 가는 값이 그것이라서. */
    private String savedNicknameFor(String kakaoNickname, String providerId, Set<String> taken) {
        when(userRepository.findByProviderAndProviderId(any(), anyString())).thenReturn(Optional.empty());
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByNickname(anyString())).thenAnswer(i -> taken.contains(i.getArgument(0)));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserService.SocialUserResult result = service.getOrCreateSocialUser(
                "a@kakao.local", kakaoNickname, null, SocialProvider.KAKAO, providerId);

        assertTrue(result.newUser(), "신규 생성이면 newUser가 true여야 한다");
        return result.user().getNickname();
    }

    @Test
    @DisplayName("특수문자·이모지를 걷어내고 최대 길이로 자른다")
    void sanitizesKakaoNickname() {
        assertEquals("홍길동님", savedNicknameFor("홍길동님♥", "1", Set.of()));
        assertEquals("sunsetjk12", savedNicknameFor("sunset_jk_1234", "1", Set.of()));
        // 서로게이트 페어(non-BMP). String.length()·substring()이 code unit 기준이라
        // 반쪽만 남으면 깨진 문자가 저장된다 — ♥ 같은 BMP 문자로는 이 경로가 안 잡힌다.
        assertEquals("사진가", savedNicknameFor("사진가😀😀", "1", Set.of()));
        assertEquals("ab", savedNicknameFor("a😀b", "1", Set.of()));
        // 한글 호환 자모(ㅋ, ㅎ)는 가-힣 밖이라 걸러진다 — 의도된 제약이다.
        assertEquals("user12345", savedNicknameFor("ㅋㅋㅋ", "12345", Set.of()));
    }

    @Test
    @DisplayName("정제 후 2자 미만이면 providerId 기반 대체 닉네임을 쓴다")
    void fallsBackWhenNothingLeft() {
        assertEquals("user847213", savedNicknameFor("♥♥♥", "9999847213", Set.of()));
        assertEquals("user7", savedNicknameFor("김", "7", Set.of()));
        assertEquals("user847213", savedNicknameFor("😀😀😀", "9999847213", Set.of()));
        assertEquals("user555", savedNicknameFor("   ", "555", Set.of()));
        assertEquals("user", savedNicknameFor(null, null, Set.of()));
        // providerId에 숫자가 없으면 접미사 없이 "user"로 떨어진다(2자 이상이라 규칙은 만족).
        assertEquals("user", savedNicknameFor("♥♥", "kakao_abc", Set.of()));
    }

    @Test
    @DisplayName("이미 쓰이는 닉네임이면 숫자를 붙인다")
    void appendsSuffixOnCollision() {
        assertEquals("홍길동2", savedNicknameFor("홍길동", "1", Set.of("홍길동")));
        assertEquals("홍길동3", savedNicknameFor("홍길동", "1", Set.of("홍길동", "홍길동2")));
    }

    @Test
    @DisplayName("접미사를 붙여도 최대 길이를 넘지 않는다")
    void suffixKeepsMaxLength() {
        String nickname = savedNicknameFor("abcdefghij", "1", Set.of("abcdefghij"));
        assertEquals("abcdefghi2", nickname);
        assertEquals(10, nickname.length());
    }

    @Test
    @DisplayName("재로그인 시 닉네임을 카카오 이름으로 덮지 않는다")
    void keepsNicknameOnRelogin() {
        User existing = mock(User.class);
        when(userRepository.findByProviderAndProviderId(any(), anyString())).thenReturn(Optional.of(existing));

        UserService.SocialUserResult result = service.getOrCreateSocialUser(
                "a@kakao.local", "카카오이름", "https://img/new.jpg", SocialProvider.KAKAO, "1");

        assertFalse(result.newUser(), "기존 계정이면 newUser가 false여야 한다");
        verify(existing).updateSocialProfile("https://img/new.jpg");
        verify(userRepository, never()).save(any(User.class));
    }
}
