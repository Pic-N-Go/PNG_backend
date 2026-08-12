package com.project.picngo.auth.service;

import com.project.picngo.auth.domain.AccessTokenValidationResult;
import com.project.picngo.common.exception.code.AuthErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final String BEARER_PREFIX = "Bearer ";
	public static final String AUTH_ERROR_CODE_ATTRIBUTE = "AUTH_ERROR_CODE";

	private final JwtTokenProvider jwtTokenProvider;
	private final CustomUserDetailsService userDetailsService;

	@Override
	protected void doFilterInternal(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain filterChain
	) throws ServletException, IOException {
		String token = resolveToken(request);

		if (token == null) {
			request.setAttribute(AUTH_ERROR_CODE_ATTRIBUTE, AuthErrorCode.ACCESS_TOKEN_REQUIRED);

            filterChain.doFilter(request, response);
            return;
        }

		AccessTokenValidationResult result = jwtTokenProvider.validateAccessTokenResult(token);

		switch (result) {
			case VALID -> setAuthentication(token);

			case EXPIRED -> request.setAttribute(
				AUTH_ERROR_CODE_ATTRIBUTE,
				AuthErrorCode.ACCESS_TOKEN_EXPIRED
			);

			case INVALID -> request.setAttribute(
				AUTH_ERROR_CODE_ATTRIBUTE,
				AuthErrorCode.ACCESS_TOKEN_INVALID
			);
		}

		filterChain.doFilter(request, response);
	}

	private void setAuthentication(String token) {
		Long userId = jwtTokenProvider.getUserId(token);

		CustomUserDetails userDetails = (CustomUserDetails) userDetailsService.loadUserById(userId);

		UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
				userDetails,
				null,
				userDetails.getAuthorities()
			);

		SecurityContextHolder.getContext().setAuthentication(authentication);
	}

	private String resolveToken(HttpServletRequest request) {
		String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);

		if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
			return null;
		}

		return authorization.substring(BEARER_PREFIX.length());
	}
}
