package com.project.picngo.auth.service;

import com.project.picngo.auth.domain.AccessTokenValidationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@RequiredArgsConstructor
public class JwtStompChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String BROKER_DESTINATION_PREFIX = "/topic";
    private static final String AUTHENTICATION_ATTRIBUTE = "AUTHENTICATION";

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService userDetailsService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        if (StompCommand.CONNECT == accessor.getCommand()) {
            Authentication authentication = authenticate(accessor);
            return applyAuthentication(message, accessor, authentication);
        }

        if (StompCommand.SEND == accessor.getCommand()) {
            validateSendDestination(accessor);
            Authentication connectedAuthentication = getSessionAuthentication(accessor);
            Authentication currentAuthentication = authenticate(accessor);
            validateSameUser(connectedAuthentication, currentAuthentication);
            return applyAuthentication(message, accessor, currentAuthentication);
        }

        if (accessor.getUser() == null && accessor.getSessionAttributes() != null) {
            Object authentication = accessor.getSessionAttributes().get(AUTHENTICATION_ATTRIBUTE);

            if (authentication instanceof Authentication auth) {
                accessor.setUser(auth);
                return MessageBuilder.createMessage(message.getPayload(), accessor.getMessageHeaders());
            }
        }

        return message;
    }

    private void validateSendDestination(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();

        if (destination != null && destination.startsWith(BROKER_DESTINATION_PREFIX)) {
            throw new AccessDeniedException("클라이언트는 메시지 구독 경로로 직접 전송할 수 없습니다.");
        }
    }

    private Message<?> applyAuthentication(
            Message<?> message,
            StompHeaderAccessor accessor,
            Authentication authentication
    ) {
        accessor.setUser(authentication);

        if (accessor.getSessionAttributes() != null) {
            accessor.getSessionAttributes().put(AUTHENTICATION_ATTRIBUTE, authentication);
        }

        return MessageBuilder.createMessage(message.getPayload(), accessor.getMessageHeaders());
    }

    private Authentication getSessionAuthentication(StompHeaderAccessor accessor) {
        if (accessor.getSessionAttributes() != null) {
            Object authentication = accessor.getSessionAttributes().get(AUTHENTICATION_ATTRIBUTE);
            if (authentication instanceof Authentication auth) {
                return auth;
            }
        }

        if (accessor.getUser() instanceof Authentication authentication) {
            return authentication;
        }

        throw new AccessDeniedException("WebSocket 연결 인증 정보를 찾을 수 없습니다.");
    }

    private void validateSameUser(Authentication connected, Authentication current) {
        if (!(connected.getPrincipal() instanceof CustomUserDetails connectedUser)
                || !(current.getPrincipal() instanceof CustomUserDetails currentUser)
                || !Objects.equals(connectedUser.getId(), currentUser.getId())) {
            throw new AccessDeniedException("WebSocket 연결 사용자와 현재 로그인 사용자가 일치하지 않습니다.");
        }
    }

    private Authentication authenticate(StompHeaderAccessor accessor) {
        String authorization = accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION);

        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw new AccessDeniedException("WebSocket 인증 토큰이 필요합니다.");
        }

        String token = authorization.substring(BEARER_PREFIX.length());

        AccessTokenValidationResult validationResult = jwtTokenProvider.validateAccessTokenResult(token);

        if (validationResult == AccessTokenValidationResult.EXPIRED) {
            throw new AccessDeniedException("만료된 WebSocket Access Token입니다.");
        }

        if (validationResult != AccessTokenValidationResult.VALID) {
            throw new AccessDeniedException("유효하지 않은 WebSocket 인증 토큰입니다.");
        }

        Long userId = jwtTokenProvider.getUserId(token);
        CustomUserDetails userDetails = (CustomUserDetails) userDetailsService.loadUserById(userId);

        return new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                userDetails.getAuthorities()
        );
    }
}
