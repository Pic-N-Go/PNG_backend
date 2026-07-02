package com.project.picngo.chat.service;


import com.project.picngo.chat.dto.ChatParticipantResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ChatParticipantService {

    private static final String PARTICIPANTS_KEY_PREFIX = "chat:room:";
    private static final String PARTICIPANTS_KEY_SUFFIX = ":participants";

    private final StringRedisTemplate redisTemplate;

    public void enter(Long spotId, Long userId, String nickname) {
        redisTemplate.opsForSet().add(key(spotId), value(userId, nickname));
    }

    public void leave(Long spotId, Long userId, String nickname) {
        redisTemplate.opsForSet().remove(key(spotId), value(userId, nickname));
    }

    public long getParticipantCount(Long spotId) {
        Long size = redisTemplate.opsForSet().size(key(spotId));
        return size == null ? 0L : size;
    }

    public List<ChatParticipantResponse> getParticipants(Long spotId) {
        Set<String> values = redisTemplate.opsForSet().members(key(spotId));

        if (values == null) {
            return List.of();
        }

        return values.stream()
                .map(ChatParticipantResponse::from)
                .toList();
    }

    private String key(Long spotId) {
        return PARTICIPANTS_KEY_PREFIX + spotId + PARTICIPANTS_KEY_SUFFIX;
    }

    private String value(Long userId, String nickname) {
        return userId + ":" + nickname;
    }
}
