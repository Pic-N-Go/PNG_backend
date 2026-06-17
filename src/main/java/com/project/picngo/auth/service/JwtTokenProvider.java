package com.project.picngo.auth.service;

import com.project.picngo.user.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtTokenProvider {

	@Value("${jwt.secret}")
	private String secret;

	@Value("${jwt.access-token-expiration-seconds}")
	private long accessTokenExpirationSeconds;

	public String createAccessToken(User user) {
		Instant now = Instant.now();
		Instant expiresAt = now.plusSeconds(accessTokenExpirationSeconds);

		return Jwts.builder()
			.subject(user.getEmail())
			.claim("userId", user.getId())
			.claim("role", user.getRole().name())
			.issuedAt(Date.from(now))
			.expiration(Date.from(expiresAt))
			.signWith(secretKey())
			.compact();
	}


	public Long getUserId(String token) {
		return parseClaims(token).get("userId", Long.class);
	}

	public boolean validateToken(String token) {
		try {
			parseClaims(token);
			return true;
		} catch (RuntimeException ex) {
			return false;
		}
	}

	public long getAccessTokenExpirationSeconds() {
		return accessTokenExpirationSeconds;
	}

	private Claims parseClaims(String token) {
		return Jwts.parser()
			.verifyWith(secretKey())
			.build()
			.parseSignedClaims(token)
			.getPayload();
	}

	private SecretKey secretKey() {
		return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
	}
}
