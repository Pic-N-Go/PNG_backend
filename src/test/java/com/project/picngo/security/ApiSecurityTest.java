package com.project.picngo.security;

import com.google.firebase.messaging.FirebaseMessaging;
import com.project.picngo.auth.service.CustomUserDetails;
import com.project.picngo.config.FcmConfig;
import com.project.picngo.notification.service.FcmService;
import com.project.picngo.notification.service.NotificationService;
import com.project.picngo.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API 보안 테스트 클래스
 * 유효한 UserDetails를 가진 인증된 요청과 인증되지 않은 요청을 MockMvc를 통해 테스트합니다.
 * Mockito를 사용하여 가짜(Mock)로 대체하여 테스트 환경에서 초기화를 우회합니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
public class ApiSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    // Firebase 관련 빈들을 가짜(Mock)로 대체하여 테스트 환경에서 Firebase 초기화를 우회합니다.
    @MockitoBean
    private FcmConfig fcmConfig;

    @MockitoBean
    private FirebaseMessaging firebaseMessaging;

    @MockitoBean
    private FcmService fcmService;

    @MockitoBean
    private NotificationService notificationService;

    // ========== 1. 인증 없이 API 호출 시 차단되는지 검증 ==========

    @Test
    @DisplayName("알림 API - 토큰 없이 호출하면 401/403 에러가 발생한다")
    void 알림_API_토큰_없이_호출시_차단() throws Exception {
        mockMvc.perform(get("/notifications"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("위시리스트 API - 토큰 없이 호출하면 차단된다")
    void 위시리스트_API_토큰_없이_호출시_차단() throws Exception {
        mockMvc.perform(get("/wishlists"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("코스 API - 토큰 없이 호출하면 차단된다")
    void 코스_API_토큰_없이_호출시_차단() throws Exception {
        mockMvc.perform(get("/courses"))
                .andExpect(status().is4xxClientError());
    }

    // ========== 2. 유효한 인증 정보로 API 호출 시 정상 응답되는지 검증 ==========

    @Test
    @DisplayName("알림 API - 유효한 인증 정보로 호출하면 200 OK가 반환된다")
    void 알림_API_유효한_인증으로_호출시_정상_응답() throws Exception {
        // given: 가짜 User → CustomUserDetails 생성
        User mockUser = Mockito.mock(User.class);
        Mockito.when(mockUser.getId()).thenReturn(1L);
        Mockito.when(mockUser.getEmail()).thenReturn("test@test.com");
        Mockito.when(mockUser.getPassword()).thenReturn("password");

        CustomUserDetails userDetails = CustomUserDetails.from(mockUser, Collections.emptyList());

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

        // when & then
        mockMvc.perform(get("/notifications")
                        .with(authentication(auth)))
                .andExpect(status().isOk());
    }
}
