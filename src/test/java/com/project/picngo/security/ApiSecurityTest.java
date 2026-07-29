package com.project.picngo.security;

import com.google.firebase.messaging.FirebaseMessaging;
import com.project.picngo.auth.service.CustomUserDetails;
import com.project.picngo.config.FcmConfig;
import com.project.picngo.course.service.CourseService;
import com.project.picngo.notification.service.FcmService;
import com.project.picngo.notification.service.NotificationService;
import com.project.picngo.user.domain.User;
import com.project.picngo.wishlist.service.WishlistService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Collections;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.sql.init.mode=never")
public class ApiSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    // Firebase 설정
    @MockitoBean
    private FcmConfig fcmConfig;

    @MockitoBean
    private FirebaseMessaging firebaseMessaging;

    @MockitoBean
    private FcmService fcmService;

    // 도메인 서비스들 (컨트롤러 로직 실행 시 NullPointerException 방지용 가짜 객체)
    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private WishlistService wishlistService;

    @MockitoBean
    private CourseService courseService;

    // ========== 1. 인증 없이 API 호출 시 엄격하게 차단되는지 검증 ==========

    @Test
    @DisplayName("알림 API - 토큰 없이 호출하면 403 권한 없음 에러가 발생한다")
    void 알림_API_토큰_없이_호출시_차단() throws Exception {
        mockMvc.perform(get("/notifications"))
                .andExpect(status().isForbidden()); // 엄격하게 401(Unauthorized)만 허용
    }

    @Test
    @DisplayName("위시리스트 API - 토큰 없이 호출하면 403 권한 없음 에러가 발생한다")
    void 위시리스트_API_토큰_없이_호출시_차단() throws Exception {
        mockMvc.perform(get("/wishlist")) // 오타가 났다면 404가 떠서 이 테스트는 실패함
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("코스 API - 토큰 없이 호출하면 403 권한 없음 에러가 발생한다")
    void 코스_API_토큰_없이_호출시_차단() throws Exception {
        mockMvc.perform(get("/courses"))
                .andExpect(status().isForbidden());
    }

    // ========== 2. 유효한 인증 정보로 API 호출 시 정상 통과되는지 검증 ==========
    // 주소 오타(404)를 원천 차단하기 위한 짝꿍 테스트!

    private UsernamePasswordAuthenticationToken createMockAuthToken() {
        User mockUser = Mockito.mock(User.class);
        Mockito.when(mockUser.getId()).thenReturn(1L);
        Mockito.when(mockUser.getEmail()).thenReturn("test@test.com");
        Mockito.when(mockUser.getPassword()).thenReturn("password");

        CustomUserDetails userDetails = CustomUserDetails.from(mockUser, Collections.emptyList());
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }

    @Test
    @DisplayName("알림 API - 유효한 인증 정보로 호출하면 200 OK가 반환된다")
    void 알림_API_유효한_인증으로_호출시_정상_응답() throws Exception {
        mockMvc.perform(get("/notifications")
                        .with(authentication(createMockAuthToken())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("위시리스트 API - 유효한 인증 정보로 호출하면 200 OK가 반환된다")
    void 위시리스트_API_유효한_인증으로_호출시_정상_응답() throws Exception {
        mockMvc.perform(get("/wishlist")
                        .with(authentication(createMockAuthToken())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("코스 API - 유효한 인증 정보로 호출하면 200 OK가 반환된다")
    void 코스_API_유효한_인증으로_호출시_정상_응답() throws Exception {
        mockMvc.perform(get("/courses")
                        .with(authentication(createMockAuthToken())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("커뮤니티 게시글 목록은 인증 없이 조회할 수 있다")
    void communityPostsCanBeReadWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/posts"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("커뮤니티 게시글 작성은 인증 없이 호출할 수 없다")
    void communityPostCannotBeCreatedWithoutAuthentication() throws Exception {
        MockMultipartFile request = new MockMultipartFile(
                "request",
                "",
                APPLICATION_JSON.toString(),
                """
                        {
                          "content": "test post",
                          "shootingTime": "05:30:00",
                          "weather": "CLEAR",
                          "tags": []
                        }
                        """.getBytes()
        );
        MockMultipartFile image = new MockMultipartFile(
                "images",
                "photo.jpg",
                "image/jpeg",
                new byte[]{1}
        );

        mockMvc.perform(multipart("/posts").file(request).file(image))
                .andExpect(status().isForbidden());
    }
}
