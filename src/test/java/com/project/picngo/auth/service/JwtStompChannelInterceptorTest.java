package com.project.picngo.auth.service;

import com.project.picngo.auth.domain.AccessTokenValidationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtStompChannelInterceptorTest {

    @Mock JwtTokenProvider jwtTokenProvider;
    @Mock CustomUserDetailsService userDetailsService;
    @Mock MessageChannel channel;

    @InjectMocks JwtStompChannelInterceptor interceptor;

    @Test
    @DisplayName("CONNECT의 유효한 Access Token으로 사용자를 인증한다")
    void authenticatesConnectWithValidAccessToken() {
        CustomUserDetails userDetails = userDetails(1L);
        when(jwtTokenProvider.validateAccessTokenResult("access-token"))
                .thenReturn(AccessTokenValidationResult.VALID);
        when(jwtTokenProvider.getUserId("access-token")).thenReturn(1L);
        when(userDetailsService.loadUserById(1L)).thenReturn(userDetails);
        Map<String, Object> sessionAttributes = new HashMap<>();

        Message<?> result = interceptor.preSend(
                stompMessage(StompCommand.CONNECT, "access-token", sessionAttributes),
                channel
        );

        Authentication authentication = (Authentication) StompHeaderAccessor.wrap(result).getUser();
        assertEquals(userDetails, authentication.getPrincipal());
        assertTrue(sessionAttributes.values().contains(authentication));
    }

    @Test
    @DisplayName("SEND 시 만료된 Access Token을 거부한다")
    void rejectsSendWithExpiredAccessToken() {
        Map<String, Object> sessionAttributes = connectedSession(1L);
        when(jwtTokenProvider.validateAccessTokenResult("expired-token"))
                .thenReturn(AccessTokenValidationResult.EXPIRED);

        AccessDeniedException exception = assertThrows(
                AccessDeniedException.class,
                () -> interceptor.preSend(
                        stompMessage(StompCommand.SEND, "expired-token", sessionAttributes),
                        channel
                )
        );

        assertEquals("만료된 WebSocket Access Token입니다.", exception.getMessage());
        verify(userDetailsService, never()).loadUserById(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("SEND에 Access Token이 없으면 요청을 거부한다")
    void rejectsSendWithoutAccessToken() {
        Map<String, Object> sessionAttributes = connectedSession(1L);
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setSessionAttributes(sessionAttributes);
        Message<byte[]> message = MessageBuilder.createMessage(
                new byte[0],
                accessor.getMessageHeaders()
        );

        assertThrows(
                AccessDeniedException.class,
                () -> interceptor.preSend(message, channel)
        );

        verify(userDetailsService, never()).loadUserById(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("같은 사용자의 갱신된 Access Token으로 SEND를 허용한다")
    void allowsSendWithRefreshedAccessTokenForSameUser() {
        Map<String, Object> sessionAttributes = connectedSession(1L);
        CustomUserDetails refreshedUser = userDetails(1L);
        when(jwtTokenProvider.validateAccessTokenResult("refreshed-token"))
                .thenReturn(AccessTokenValidationResult.VALID);
        when(jwtTokenProvider.getUserId("refreshed-token")).thenReturn(1L);
        when(userDetailsService.loadUserById(1L)).thenReturn(refreshedUser);

        Message<?> result = interceptor.preSend(
                stompMessage(StompCommand.SEND, "refreshed-token", sessionAttributes),
                channel
        );

        Authentication authentication = (Authentication) StompHeaderAccessor.wrap(result).getUser();
        assertEquals(refreshedUser, authentication.getPrincipal());
        assertTrue(sessionAttributes.values().contains(authentication));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/topic/chats/1",
            "/topic/chats/1/participants/count",
            "/topic-custom"
    })
    @DisplayName("클라이언트가 /topic 접두사의 경로로 직접 SEND하면 거부한다")
    void rejectsDirectSendToBrokerDestination(String destination) {
        Map<String, Object> sessionAttributes = connectedSession(1L);
        Message<byte[]> message = stompMessage(
                StompCommand.SEND,
                "access-token",
                sessionAttributes,
                destination
        );

        AccessDeniedException exception = assertThrows(
                AccessDeniedException.class,
                () -> interceptor.preSend(message, channel)
        );

        assertEquals(
                "클라이언트는 메시지 구독 경로로 직접 전송할 수 없습니다.",
                exception.getMessage()
        );
        verify(jwtTokenProvider, never())
                .validateAccessTokenResult(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("WebSocket 연결 사용자와 다른 사용자의 Access Token으로 SEND할 수 없다")
    void rejectsSendWithAccessTokenForDifferentUser() {
        Map<String, Object> sessionAttributes = connectedSession(1L);
        CustomUserDetails differentUser = userDetails(2L);
        when(jwtTokenProvider.validateAccessTokenResult("different-user-token"))
                .thenReturn(AccessTokenValidationResult.VALID);
        when(jwtTokenProvider.getUserId("different-user-token")).thenReturn(2L);
        when(userDetailsService.loadUserById(2L)).thenReturn(differentUser);

        assertThrows(
                AccessDeniedException.class,
                () -> interceptor.preSend(
                        stompMessage(StompCommand.SEND, "different-user-token", sessionAttributes),
                        channel
                )
        );
    }

    private Message<byte[]> stompMessage(
            StompCommand command,
            String accessToken,
            Map<String, Object> sessionAttributes
    ) {
        return stompMessage(command, accessToken, sessionAttributes, "/app/chats/1/messages");
    }

    private Message<byte[]> stompMessage(
            StompCommand command,
            String accessToken,
            Map<String, Object> sessionAttributes,
            String destination
    ) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setSessionAttributes(sessionAttributes);
        if (command == StompCommand.SEND) {
            accessor.setDestination(destination);
        }
        accessor.setNativeHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Map<String, Object> connectedSession(Long userId) {
        CustomUserDetails userDetails = userDetails(userId);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                userDetails.getAuthorities()
        );
        Map<String, Object> sessionAttributes = new HashMap<>();
        sessionAttributes.put("AUTHENTICATION", authentication);
        return sessionAttributes;
    }

    private CustomUserDetails userDetails(Long userId) {
        CustomUserDetails userDetails = mock(CustomUserDetails.class);
        org.mockito.Mockito.lenient().when(userDetails.getId()).thenReturn(userId);
        return userDetails;
    }
}
