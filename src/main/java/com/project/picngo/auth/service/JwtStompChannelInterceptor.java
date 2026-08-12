package com.project.picngo.auth.service;

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

@Component
@RequiredArgsConstructor
public class JwtStompChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String AUTHENTICATION_ATTRIBUTE = "AUTHENTICATION";

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService userDetailsService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        if (StompCommand.CONNECT == accessor.getCommand()) {
            Authentication authentication = authenticate(accessor);
            accessor.setUser(authentication);

            if (accessor.getSessionAttributes() != null) {
                accessor.getSessionAttributes().put(AUTHENTICATION_ATTRIBUTE, authentication);
            }

            return MessageBuilder.createMessage(message.getPayload(), accessor.getMessageHeaders());
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

    private Authentication authenticate(StompHeaderAccessor accessor) {
        String authorization = accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION);

        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw new AccessDeniedException("WebSocket 인증 토큰이 필요합니다.");
        }

        String token = authorization.substring(BEARER_PREFIX.length());

        if (!jwtTokenProvider.validateAccessToken(token)) {
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
