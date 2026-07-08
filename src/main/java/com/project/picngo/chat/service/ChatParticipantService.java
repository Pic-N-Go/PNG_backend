package com.project.picngo.chat.service;


import com.project.picngo.chat.dto.ChatParticipantResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ChatParticipantService {

    private static final String PARTICIPANTS_KEY_PREFIX = "chat:room:";
    private static final String PARTICIPANTS_KEY_SUFFIX = ":participants";

    private final StringRedisTemplate redisTemplate;

    public void enter(Long spotId, Long userId, String nickname) {
        redisTemplate.opsForHash().put(key(spotId), String.valueOf(userId), nickname);
    }

    public void leave(Long spotId, Long userId) {
        redisTemplate.opsForHash().delete(key(spotId), String.valueOf(userId));
    }

    public long getParticipantCount(Long spotId) {
        Long size = redisTemplate.opsForHash().size(key(spotId));
        return size == null ? 0L : size;
    }

    //Todo : 사용자가 많아진다면
    public List<ChatParticipantResponse> getParticipants(Long spotId) {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key(spotId));

        return entries.entrySet().stream()
                .map(entry -> new ChatParticipantResponse(
                        Long.valueOf((String) entry.getKey()),
                        (String) entry.getValue()
                ))
                .toList();
    }

    private String key(Long spotId) {
        return PARTICIPANTS_KEY_PREFIX + spotId + PARTICIPANTS_KEY_SUFFIX;
    }

}
