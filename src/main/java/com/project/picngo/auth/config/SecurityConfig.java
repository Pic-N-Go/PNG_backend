package com.project.picngo.auth.config;

import com.project.picngo.auth.service.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

	private static final String[] PUBLIC_ENDPOINTS = {
		"/auth/**",
		"/version/check",
		"/categories",
		"/spots/**",
		"/bookmark-collections/**", // Spot Detail: TEMP_USER_ID 기반, 실 인증 연동 시 제거 필요

		"/swagger-ui/**",
		"/v3/api-docs/**",
		"/swagger-resources/**",
		"/ws",
		"/ws/**",
		"/notifications/test/**"
	};

	private final JwtAuthenticationFilter jwtAuthenticationFilter;

	// loadtest 프로파일에서만 존재하는 빈. 운영에서는 비어 있어 아무 엔드포인트도 열리지 않는다.
	private final ObjectProvider<LoadTestPublicEndpoints> loadTestPublicEndpoints;

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		return http
			.cors(cors -> cors.configurationSource(corsConfigurationSource()))
			.csrf(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> {
				auth
					.requestMatchers(HttpMethod.GET, "/posts", "/posts/**").permitAll()
					// 관심테마 기반 개인화 추천이라 로그인 필요. PUBLIC_ENDPOINTS의 /spots/** 보다 먼저 와야 적용된다.
					.requestMatchers(HttpMethod.GET, "/spots/recommended").authenticated()
					.requestMatchers(HttpMethod.POST, "/spots/*/reviews").authenticated()
					.requestMatchers(HttpMethod.PUT, "/reviews/**").authenticated()
					.requestMatchers(HttpMethod.DELETE, "/reviews/**").authenticated()
					.requestMatchers(PUBLIC_ENDPOINTS).permitAll();

				// loadtest 프로파일일 때만 부하테스트/관리자용 엔드포인트를 추가로 연다.
				LoadTestPublicEndpoints extra = loadTestPublicEndpoints.getIfAvailable();
				if (extra != null) {
					auth.requestMatchers(extra.patterns().toArray(String[]::new)).permitAll();
				}

				auth.anyRequest().authenticated();
			})
			.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
			.build();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration config = new CorsConfiguration();
		config.addAllowedOriginPattern("http://localhost:*");
		config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
		config.setAllowedHeaders(List.of("*"));
		config.setAllowCredentials(true);
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", config);
		return source;
	}
}
