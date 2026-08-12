package com.project.picngo.chat.event;

import com.project.picngo.chat.service.ChatParticipantService;
import com.project.picngo.chat.service.ChatRoomService;
import com.project.picngo.common.event.SpotCreatedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChatRoomEventListenerTest {

    @Mock ChatRoomService chatRoomService;
    @Mock SimpMessagingTemplate messagingTemplate;
    @Mock ChatParticipantService chatParticipantService;

    @InjectMocks ChatRoomEventListener listener;

    @Test
    @DisplayName("스팟 생성 이벤트를 받으면 해당 스팟의 채팅방을 생성한다")
    void handleSpotCreatedCreatesChatRoom() {
        listener.handleSpotCreated(new SpotCreatedEvent(7L));

        verify(chatRoomService).createForSpot(7L);
    }
}
