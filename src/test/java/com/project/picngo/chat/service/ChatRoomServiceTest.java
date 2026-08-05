package com.project.picngo.chat.service;

import com.project.picngo.chat.domain.ChatRoom;
import com.project.picngo.chat.repository.ChatRoomRepository;
import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.ChatRoomErrorCode;
import com.project.picngo.common.exception.code.SpotErrorCode;
import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.repository.SpotRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatRoomServiceTest {

    @Mock ChatRoomRepository chatRoomRepository;
    @Mock SpotRepository spotRepository;

    @InjectMocks ChatRoomService chatRoomService;

    @Test
    @DisplayName("이미 채팅방이 존재하면 새로 생성하지 않고 기존 채팅방을 반환한다")
    void createForSpotReturnsExistingRoom() {
        ChatRoom existingRoom = org.mockito.Mockito.mock(ChatRoom.class);
        when(chatRoomRepository.findBySpot_Id(1L)).thenReturn(Optional.of(existingRoom));

        ChatRoom result = chatRoomService.createForSpot(1L);

        assertSame(existingRoom, result);
        verifyNoInteractions(spotRepository);
        verify(chatRoomRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("채팅방이 없으면 스팟과 연결된 활성 채팅방을 생성한다")
    void createForSpotCreatesRoomForSpot() {
        Spot spot = org.mockito.Mockito.mock(Spot.class);
        when(chatRoomRepository.findBySpot_Id(1L)).thenReturn(Optional.empty());
        when(spotRepository.findById(1L)).thenReturn(Optional.of(spot));
        when(chatRoomRepository.save(org.mockito.ArgumentMatchers.any(ChatRoom.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ChatRoom result = chatRoomService.createForSpot(1L);

        ArgumentCaptor<ChatRoom> captor = ArgumentCaptor.forClass(ChatRoom.class);
        verify(chatRoomRepository).save(captor.capture());
        assertSame(result, captor.getValue());
        assertSame(spot, result.getSpot());
        assertEquals(true, result.isActive());
    }

    @Test
    @DisplayName("채팅방을 만들 스팟이 없으면 SPOT_NOT_FOUND 예외가 발생한다")
    void createForSpotRejectsMissingSpot() {
        when(chatRoomRepository.findBySpot_Id(404L)).thenReturn(Optional.empty());
        when(spotRepository.findById(404L)).thenReturn(Optional.empty());

        CustomException exception = assertThrows(
                CustomException.class,
                () -> chatRoomService.createForSpot(404L)
        );

        assertEquals(SpotErrorCode.SPOT_NOT_FOUND, exception.getErrorCode());
        verify(chatRoomRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("스팟의 채팅방이 없으면 CHAT_ROOM_NOT_FOUND 예외가 발생한다")
    void getBySpotIdRejectsMissingRoom() {
        when(chatRoomRepository.findBySpot_Id(404L)).thenReturn(Optional.empty());

        CustomException exception = assertThrows(
                CustomException.class,
                () -> chatRoomService.getBySpotId(404L)
        );

        assertEquals(ChatRoomErrorCode.CHAT_ROOM_NOT_FOUND, exception.getErrorCode());
    }
}
