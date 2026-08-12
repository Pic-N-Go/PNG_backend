package com.project.picngo.chat.service;

import com.project.picngo.chat.domain.ChatMessage;
import com.project.picngo.chat.domain.ChatMessageType;
import com.project.picngo.chat.domain.ChatRoom;
import com.project.picngo.chat.dto.ChatMessageResponse;
import com.project.picngo.chat.dto.ChatMessageSendRequest;
import com.project.picngo.chat.repository.ChatMessageRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatMessageServiceTest {

    @Mock ChatRoomService chatRoomService;
    @Mock ChatMessageRepository chatMessageRepository;

    @InjectMocks ChatMessageService chatMessageService;

    @Test
    @DisplayName("메시지를 해당 스팟의 채팅방에 저장하고 응답으로 반환한다")
    void sendMessageSavesTextMessage() {
        ChatRoom room = mock(ChatRoom.class);
        when(room.getId()).thenReturn(10L);
        when(chatRoomService.getBySpotId(1L)).thenReturn(room);
        when(chatMessageRepository.save(org.mockito.ArgumentMatchers.any(ChatMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ChatMessageResponse response = chatMessageService.sendMessage(
                1L,
                2L,
                "여행자",
                new ChatMessageSendRequest("안녕하세요")
        );

        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository).save(captor.capture());
        ChatMessage savedMessage = captor.getValue();
        assertEquals(10L, savedMessage.getChatRoomId());
        assertEquals(2L, savedMessage.getSenderId());
        assertEquals("여행자", savedMessage.getSenderNickname());
        assertEquals(ChatMessageType.TEXT, savedMessage.getType());
        assertEquals("안녕하세요", savedMessage.getContent());
        assertEquals("안녕하세요", response.content());
    }

    @Test
    @DisplayName("최근 메시지 50개를 오래된 메시지부터 정렬해 반환한다")
    void getMessagesReturnsChronologicalOrder() {
        ChatRoom room = roomWithId(10L);
        ChatMessage newest = message(3L, "셋째");
        ChatMessage middle = message(2L, "둘째");
        ChatMessage oldest = message(1L, "첫째");
        when(chatRoomService.getBySpotId(1L)).thenReturn(room);
        when(chatMessageRepository.findTop50ByChatRoomIdOrderByCreatedAtDesc(10L))
                .thenReturn(new ArrayList<>(List.of(newest, middle, oldest)));

        List<ChatMessageResponse> result = chatMessageService.getMessages(1L);

        assertEquals(List.of("첫째", "둘째", "셋째"),
                result.stream().map(ChatMessageResponse::content).toList());
    }

    @Test
    @DisplayName("미리보기 메시지 3개를 오래된 메시지부터 정렬해 반환한다")
    void getPreviewMessagesReturnsChronologicalOrder() {
        ChatRoom room = roomWithId(10L);
        ChatMessage newest = message(3L, "셋째");
        ChatMessage middle = message(2L, "둘째");
        ChatMessage oldest = message(1L, "첫째");
        when(chatRoomService.getBySpotId(1L)).thenReturn(room);
        when(chatMessageRepository.findTop3ByChatRoomIdOrderByCreatedAtDesc(10L))
                .thenReturn(new ArrayList<>(List.of(newest, middle, oldest)));

        List<ChatMessageResponse> result = chatMessageService.getPreviewMessages(1L);

        assertEquals(List.of("첫째", "둘째", "셋째"),
                result.stream().map(ChatMessageResponse::content).toList());
    }

    private ChatRoom roomWithId(Long id) {
        ChatRoom room = mock(ChatRoom.class);
        when(room.getId()).thenReturn(id);
        return room;
    }

    private ChatMessage message(Long id, String content) {
        ChatMessage message = mock(ChatMessage.class);
        when(message.getId()).thenReturn(id);
        when(message.getContent()).thenReturn(content);
        when(message.getType()).thenReturn(ChatMessageType.TEXT);
        return message;
    }
}
