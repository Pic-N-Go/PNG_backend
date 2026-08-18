package com.project.picngo.auth.service;

import com.project.picngo.auth.domain.AccessTokenValidationResult;
import com.project.picngo.common.exception.code.AuthErrorCode;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private CustomUserDetailsService userDetailsService;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("액세스 토큰이 없으면 필수 오류 코드를 기록하고 요청을 계속 진행한다")
    void recordsRequiredErrorWhenTokenIsMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        assertThat(request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_CODE_ATTRIBUTE))
                .isEqualTo(AuthErrorCode.ACCESS_TOKEN_REQUIRED);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("만료된 액세스 토큰이면 만료 오류 코드를 기록한다")
    void recordsExpiredErrorWhenTokenIsExpired() throws Exception {
        MockHttpServletRequest request = requestWithBearerToken("expired-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(jwtTokenProvider.validateAccessTokenResult("expired-token"))
                .thenReturn(AccessTokenValidationResult.EXPIRED);

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        assertThat(request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_CODE_ATTRIBUTE))
                .isEqualTo(AuthErrorCode.ACCESS_TOKEN_EXPIRED);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("유효하지 않은 액세스 토큰이면 유효성 오류 코드를 기록한다")
    void recordsInvalidErrorWhenTokenIsInvalid() throws Exception {
        MockHttpServletRequest request = requestWithBearerToken("invalid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(jwtTokenProvider.validateAccessTokenResult("invalid-token"))
                .thenReturn(AccessTokenValidationResult.INVALID);

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        assertThat(request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_CODE_ATTRIBUTE))
                .isEqualTo(AuthErrorCode.ACCESS_TOKEN_INVALID);
        verify(filterChain).doFilter(request, response);
    }

    private MockHttpServletRequest requestWithBearerToken(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }
}
