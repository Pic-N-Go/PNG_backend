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
import java.util.UUID;

@Component
public class JwtTokenProvider {

    private static final String TOKEN_TYPE = "tokenType";
    private static final String ACCESS = "ACCESS";
    private static final String REFRESH = "REFRESH";

	@Value("${jwt.secret}")
	private String secret;

	@Value("${jwt.access-token-expiration-seconds}")
	private long accessTokenExpirationSeconds;

    @Value("${jwt.refresh-token-expiration-seconds}")
    private long refreshTokenExpirationSeconds;

	public String createAccessToken(User user) {
		return createToken(user, ACCESS, accessTokenExpirationSeconds);
	}

    public String createRefreshToken(User user) {
        return createToken(user, REFRESH, refreshTokenExpirationSeconds);
    }

    private String createToken(User user, String tokenType, long expirationSeconds){
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(expirationSeconds);

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(user.getEmail())
                .claim("userId", user.getId())
                .claim("role", user.getRole().name())
                .claim(TOKEN_TYPE, tokenType)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey())
                .compact();
    }

	public Long getUserId(String token) {
		return parseClaims(token).get("userId", Long.class);
	}

    public String getTokenId(String token) {
        return parseClaims(token).getId();
    }

    public boolean validateAccessToken(String token) {
        return validateTokenType(token, ACCESS);
    }

    public boolean validateRefreshToken(String token) {
        return validateTokenType(token, REFRESH);
    }

	private boolean validateTokenType(String token, String expectedType) {

		try {
			Claims claims = parseClaims(token);
            String actualType = claims.get(TOKEN_TYPE, String.class);

            return expectedType.equals(actualType);
		} catch (RuntimeException ex) {
			return false;
		}
	}

	public long getAccessTokenExpirationSeconds() {
		return accessTokenExpirationSeconds;
	}

    public long getRefreshTokenExpirationSeconds() {
        return refreshTokenExpirationSeconds;
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
