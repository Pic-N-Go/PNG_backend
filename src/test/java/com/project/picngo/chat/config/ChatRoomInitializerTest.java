package com.project.picngo.chat.config;

import com.project.picngo.chat.repository.ChatRoomRepository;
import com.project.picngo.chat.service.ChatRoomService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;

import java.util.List;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatRoomInitializerTest {

    @Mock ChatRoomRepository chatRoomRepository;
    @Mock ChatRoomService chatRoomService;
    @Mock ApplicationArguments applicationArguments;

    @InjectMocks ChatRoomInitializer initializer;

    @Test
    @DisplayName("서버 기동 시 채팅방이 없는 스팟에 대해서만 채팅방을 생성한다")
    void runCreatesRoomsForMissingSpots() {
        when(chatRoomRepository.findSpotIdWithoutChatRoom()).thenReturn(List.of(2L, 5L));

        initializer.run(applicationArguments);

        var ordered = inOrder(chatRoomService);
        ordered.verify(chatRoomService).createForSpot(2L);
        ordered.verify(chatRoomService).createForSpot(5L);
        ordered.verifyNoMoreInteractions();
    }

    @Test
    @DisplayName("누락된 채팅방이 없으면 생성 서비스를 호출하지 않는다")
    void runDoesNothingWhenNoRoomIsMissing() {
        when(chatRoomRepository.findSpotIdWithoutChatRoom()).thenReturn(List.of());

        initializer.run(applicationArguments);

        verifyNoInteractions(chatRoomService);
    }
}
