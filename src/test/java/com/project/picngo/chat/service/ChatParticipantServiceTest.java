package com.project.picngo.chat.service;

import com.project.picngo.chat.dto.ChatParticipantResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatParticipantServiceTest {

    @Mock StringRedisTemplate redisTemplate;
    @Mock HashOperations<String, Object, Object> hashOperations;

    private ChatParticipantService chatParticipantService;

    @BeforeEach
    void setUp() {
        doReturn(hashOperations).when(redisTemplate).opsForHash();
        chatParticipantService = new ChatParticipantService(redisTemplate);
    }

    @Test
    @DisplayName("입장한 사용자를 스팟 채팅방의 Redis 해시에 저장한다")
    void enterStoresParticipant() {
        chatParticipantService.enter(7L, 2L, "여행자");

        verify(hashOperations).put("chat:room:7:participants", "2", "여행자");
    }

    @Test
    @DisplayName("퇴장한 사용자를 스팟 채팅방의 Redis 해시에서 제거한다")
    void leaveRemovesParticipant() {
        chatParticipantService.leave(7L, 2L);

        verify(hashOperations).delete("chat:room:7:participants", "2");
    }

    @Test
    @DisplayName("Redis 해시 크기를 현재 참여자 수로 반환한다")
    void getParticipantCountReturnsHashSize() {
        when(hashOperations.size("chat:room:7:participants")).thenReturn(3L);

        assertEquals(3L, chatParticipantService.getParticipantCount(7L));
    }

    @Test
    @DisplayName("Redis 참여자 정보를 응답 DTO 목록으로 변환한다")
    void getParticipantsConvertsHashEntries() {
        Map<Object, Object> entries = new LinkedHashMap<>();
        entries.put("2", "첫째");
        entries.put("3", "둘째");
        when(hashOperations.entries("chat:room:7:participants")).thenReturn(entries);

        List<ChatParticipantResponse> result = chatParticipantService.getParticipants(7L);

        assertEquals(List.of(2L, 3L), result.stream().map(ChatParticipantResponse::userId).toList());
        assertEquals(List.of("첫째", "둘째"), result.stream().map(ChatParticipantResponse::nickname).toList());
    }
}
