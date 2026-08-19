package com.project.picngo.chat.controller;

import com.project.picngo.auth.service.CustomUserDetails;
import com.project.picngo.chat.domain.ChatMessageType;
import com.project.picngo.chat.dto.ChatMessageResponse;
import com.project.picngo.chat.dto.ChatMessageSendRequest;
import com.project.picngo.chat.service.ChatMessageService;
import com.project.picngo.chat.service.ChatParticipantService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatSocketControllerTest {

    @Mock ChatMessageService chatMessageService;
    @Mock SimpMessagingTemplate messagingTemplate;
    @Mock ChatParticipantService chatParticipantService;

    @InjectMocks ChatSocketController controller;

    @Test
    @DisplayName("인증 사용자의 메시지를 저장한 뒤 해당 스팟 구독자에게 전송한다")
    void sendMessageBroadcastsSavedMessage() {
        Authentication authentication = authentication(2L, "여행자");
        ChatMessageSendRequest request = new ChatMessageSendRequest("안녕하세요");
        SimpMessageHeaderAccessor accessor = accessorForSpot(7L);
        ChatMessageResponse response = new ChatMessageResponse(
                1L, 2L, "여행자", ChatMessageType.TEXT, "안녕하세요", null
        );
        when(chatMessageService.sendMessage(7L, 2L, "여행자", request)).thenReturn(response);

        controller.sendMessage(7L, request, authentication, accessor);

        verify(messagingTemplate).convertAndSend("/topic/chats/7", response);
    }

    @Test
    @DisplayName("다른 스팟 채팅방으로 이동하면 이전 방에서 퇴장하고 새 방에 입장한다")
    void enterMovesParticipantBetweenRooms() {
        Authentication authentication = authentication(2L, "여행자");
        SimpMessageHeaderAccessor accessor = mock(SimpMessageHeaderAccessor.class);
        Map<String, Object> sessionAttributes = new HashMap<>();
        sessionAttributes.put("spotId", 3L);
        when(accessor.getSessionAttributes()).thenReturn(sessionAttributes);
        when(chatParticipantService.getParticipantCount(3L)).thenReturn(1L);
        when(chatParticipantService.getParticipantCount(7L)).thenReturn(2L);

        controller.enter(7L, authentication, accessor);

        verify(chatParticipantService).leave(3L, 2L);
        verify(chatParticipantService).enter(7L, 2L, "여행자");
        verify(messagingTemplate).convertAndSend("/topic/chats/3/participants/count", 1L);
        verify(messagingTemplate).convertAndSend("/topic/chats/7/participants/count", 2L);
        org.junit.jupiter.api.Assertions.assertEquals(7L, sessionAttributes.get("spotId"));
    }

    @Test
    @DisplayName("채팅방을 나가면 참여자를 제거하고 변경된 참여자 수를 전송한다")
    void leaveRemovesParticipantAndBroadcastsCount() {
        Authentication authentication = authentication(2L, "여행자");
        SimpMessageHeaderAccessor accessor = mock(SimpMessageHeaderAccessor.class);
        Map<String, Object> sessionAttributes = new HashMap<>();
        sessionAttributes.put("spotId", 7L);
        when(accessor.getSessionAttributes()).thenReturn(sessionAttributes);
        when(chatParticipantService.getParticipantCount(7L)).thenReturn(1L);

        controller.leave(7L, authentication, accessor);

        verify(chatParticipantService).leave(7L, 2L);
        verify(messagingTemplate).convertAndSend("/topic/chats/7/participants/count", 1L);
        org.junit.jupiter.api.Assertions.assertEquals(false, sessionAttributes.containsKey("spotId"));
    }

    @Test
    @DisplayName("퇴장 요청 채팅방이 세션 채팅방과 다르면 요청을 거부한다")
    void leaveRejectsMismatchedSessionSpotId() {
        CustomUserDetails userDetails = mock(CustomUserDetails.class);
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        SimpMessageHeaderAccessor accessor = mock(SimpMessageHeaderAccessor.class);
        Map<String, Object> sessionAttributes = new HashMap<>();
        sessionAttributes.put("spotId", 7L);
        when(accessor.getSessionAttributes()).thenReturn(sessionAttributes);

        assertThrows(
                AccessDeniedException.class,
                () -> controller.leave(8L, authentication, accessor)
        );

        verify(chatParticipantService, never()).leave(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong()
        );
        verify(chatParticipantService, never()).getParticipantCount(
                org.mockito.ArgumentMatchers.anyLong()
        );
        org.mockito.Mockito.verifyNoInteractions(messagingTemplate);
        org.junit.jupiter.api.Assertions.assertEquals(7L, sessionAttributes.get("spotId"));
    }

    @Test
    @DisplayName("인증되지 않은 사용자는 메시지를 보낼 수 없다")
    void sendMessageRejectsUnauthenticatedPrincipal() {
        ChatMessageSendRequest request = new ChatMessageSendRequest("안녕하세요");

        assertThrows(
                AccessDeniedException.class,
                () -> controller.sendMessage(7L, request, null, mock(SimpMessageHeaderAccessor.class))
        );

        verify(chatMessageService, never()).sendMessage(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    @DisplayName("현재 참여 중인 채팅방과 다른 스팟에는 메시지를 보낼 수 없다")
    void sendMessageRejectsMismatchedSessionSpotId() {
        Authentication authentication = authentication(2L, "여행자");
        ChatMessageSendRequest request = new ChatMessageSendRequest("안녕하세요");

        assertThrows(
                AccessDeniedException.class,
                () -> controller.sendMessage(8L, request, authentication, accessorForSpot(7L))
        );

        verify(chatMessageService, never()).sendMessage(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()
        );
        org.mockito.Mockito.verifyNoInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("채팅방에 입장하지 않은 세션은 메시지를 보낼 수 없다")
    void sendMessageRejectsSessionWithoutSpotId() {
        Authentication authentication = authentication(2L, "여행자");
        ChatMessageSendRequest request = new ChatMessageSendRequest("안녕하세요");
        SimpMessageHeaderAccessor accessor = mock(SimpMessageHeaderAccessor.class);

        assertThrows(
                AccessDeniedException.class,
                () -> controller.sendMessage(7L, request, authentication, accessor)
        );

        verify(chatMessageService, never()).sendMessage(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()
        );
        org.mockito.Mockito.verifyNoInteractions(messagingTemplate);
    }

    private SimpMessageHeaderAccessor accessorForSpot(Long spotId) {
        SimpMessageHeaderAccessor accessor = mock(SimpMessageHeaderAccessor.class);
        Map<String, Object> sessionAttributes = new HashMap<>();
        sessionAttributes.put("spotId", spotId);
        when(accessor.getSessionAttributes()).thenReturn(sessionAttributes);
        return accessor;
    }

    private Authentication authentication(Long userId, String nickname) {
        CustomUserDetails userDetails = mock(CustomUserDetails.class);
        org.mockito.Mockito.lenient().when(userDetails.getId()).thenReturn(userId);
        org.mockito.Mockito.lenient().when(userDetails.getNickname()).thenReturn(nickname);
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        return authentication;
    }
}
