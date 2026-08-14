package com.project.picngo.security;

import com.google.firebase.messaging.FirebaseMessaging;
import com.project.picngo.auth.service.CustomUserDetails;
import com.project.picngo.config.FcmConfig;
import com.project.picngo.course.service.CourseService;
import com.project.picngo.notification.service.FcmService;
import com.project.picngo.notification.service.NotificationService;
import com.project.picngo.user.domain.User;
import com.project.picngo.spotalert.service.SpotAlertService;
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

import static org.assertj.core.api.Assertions.assertThat;
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
    private SpotAlertService spotAlertService;

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
    @DisplayName("출사알림 API - 토큰 없이 호출하면 403 권한 없음 에러가 발생한다")
    void 출사알림_API_토큰_없이_호출시_차단() throws Exception {
        mockMvc.perform(get("/spot-alerts")) // 오타가 났다면 404가 떠서 이 테스트는 실패함
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
    @DisplayName("출사알림 API - 유효한 인증 정보로 호출하면 200 OK가 반환된다")
    void 출사알림_API_유효한_인증으로_호출시_정상_응답() throws Exception {
        mockMvc.perform(get("/spot-alerts")
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

    @Test
    @DisplayName("북마크 컬렉션 목록 조회 API - 토큰 없이 호출하면 403 권한 없음 에러가 발생한다")
    void 북마크_컬렉션_목록_조회_API_토큰_없이_호출시_차단() throws Exception {
        mockMvc.perform(get("/bookmark-collections"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("스팟 북마크 컬렉션 동기화 API - 토큰 없이 호출하면 403 권한 없음 에러가 발생한다")
    void 스팟_북마크_컬렉션_동기화_API_토큰_없이_호출시_차단() throws Exception {
        mockMvc.perform(put("/spots/1/bookmark-collections")
                        .contentType(APPLICATION_JSON)
                        .content("{\"collectionIds\":[]}"))
                .andExpect(status().isForbidden());
    }

    // 위의 403(토큰 없음) 테스트들의 짝꿍: 유효한 인증 정보로 호출하면 실제로 통과되는지 검증
    // (라우트 매처를 너무 넓게 잡아 항상 403만 나던 회귀나, 주소 오타로 인한 404 은폐를 막는다)

    @Test
    @DisplayName("북마크 컬렉션 목록 조회 API - 유효한 인증 정보로 호출하면 200 OK가 반환된다")
    void 북마크_컬렉션_목록_조회_API_유효한_인증으로_호출시_정상_응답() throws Exception {
        mockMvc.perform(get("/bookmark-collections")
                        .with(authentication(createMockAuthToken())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("스팟 상세 조회 API - 토큰 없이 호출해도 403으로 차단되지 않는다 (공개 API)")
    void 스팟_상세_조회_API_토큰_없이_호출시_차단되지_않음() throws Exception {
        mockMvc.perform(get("/spots/1"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(403));
    }

    /*
     * 목록 3종은 isBookmarked를 채우려고 @AuthenticationPrincipal을 받는다. 이 경로들은 permitAll이라
     * 비로그인 요청이 정상 트래픽이고, principal 해석이 null로 떨어져야 200이 난다.
     * 파라미터 타입이 바뀌어 resolver가 매칭에 실패하면 공개 엔드포인트가 500으로 죽으므로 200을 못 박는다.
     */
    @Test
    @DisplayName("스팟 목록 API - 토큰 없이 호출해도 200이다 (공개 API, isBookmarked는 false)")
    void 스팟_목록_API_토큰_없이_호출시_200() throws Exception {
        mockMvc.perform(get("/spots").param("size", "1"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("인기 스팟 API - 토큰 없이 호출해도 200이다 (공개 API, isBookmarked는 false)")
    void 인기_스팟_API_토큰_없이_호출시_200() throws Exception {
        mockMvc.perform(get("/spots/popular").param("size", "1"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("스팟 검색 API - 토큰 없이 호출해도 200이다 (공개 API, isBookmarked는 false)")
    void 스팟_검색_API_토큰_없이_호출시_200() throws Exception {
        mockMvc.perform(get("/spots/search").param("keyword", "공원").param("size", "1"))
                .andExpect(status().isOk());
    }
}
