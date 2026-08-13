package com.project.picngo.auth.config;

import com.project.picngo.auth.service.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] PUBLIC_ENDPOINTS = {
            "/auth/**",
            "/version/check",
            "/categories",
            "/spots/**",

            "/picngo-team-api-2026.html",
            "/picngo-team-api-2026/**",
            "/picngo-team-api-data/**",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**",


            "/swagger-resources/**",
            "/ws",
            "/ws/**",
            "/notifications/test/**",
            // Docker HEALTHCHECK·로드밸런서가 인증 없이 때린다. management.endpoints에서
            // health만 노출하도록 이미 제한해뒀다(application-prod.yaml).
            "/actuator/health",
            "/actuator/prometheus"
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
					// 관리자 전용. PUBLIC_ENDPOINTS의 /spots/** 와 겹치지 않도록 경로를
					// /admin 아래로 뺐다 - /spots/... 밑에 뒀다면 전부 공개돼버린다.
					.requestMatchers("/admin/**").hasRole("ADMIN")
					.requestMatchers(HttpMethod.GET, "/posts", "/posts/**").permitAll()
					// 관심테마 기반 개인화 추천이라 로그인 필요. PUBLIC_ENDPOINTS의 /spots/** 보다 먼저 와야 적용된다.
					.requestMatchers(HttpMethod.GET, "/spots/recommended").authenticated()
					.requestMatchers(HttpMethod.POST, "/spots/*/reviews").authenticated()
					.requestMatchers(HttpMethod.PUT, "/reviews/**").authenticated()
					.requestMatchers(HttpMethod.DELETE, "/reviews/**").authenticated()
					// 북마크는 사용자별 데이터라 로그인 필요. PUBLIC_ENDPOINTS의 /spots/** 보다 먼저 와야 적용된다.
					.requestMatchers("/spots/*/bookmark-collections").authenticated()
					.requestMatchers(PUBLIC_ENDPOINTS).permitAll()
					.requestMatchers(HttpMethod.GET, "/reviews/*/exif").permitAll()
					.requestMatchers("/tour-api/**").permitAll(); // Spot Detail: 로컬 Swagger 테스트용, 배포 전 hasRole("ADMIN") 으로 변경 필요

				// loadtest 프로파일일 때만 부하테스트/관리자용 엔드포인트를 추가로 연다.
				LoadTestPublicEndpoints extra = loadTestPublicEndpoints.getIfAvailable();
				if (extra != null) {
					// 인증이 면제된 경로는 항상 로그로 드러나야 한다. 운영에서 이 줄이 보이면 잘못 뜬 것이다.
					log.warn("⚠️ [loadtest 프로파일] 인증 면제 엔드포인트가 활성화되었습니다: {}", extra.patterns());
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
