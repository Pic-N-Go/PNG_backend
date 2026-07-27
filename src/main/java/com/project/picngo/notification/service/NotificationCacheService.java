package com.project.picngo.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.project.picngo.notification.domain.NotificationSetting;
import com.project.picngo.notification.dto.NotificationSettingResponse;
import com.project.picngo.notification.repository.NotificationSettingRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class NotificationCacheService {

    private static final String SETTING_KEY_PREFIX = "noti:setting:";
    private static final String ACTIVE_USERS_KEY_PREFIX = "noti:active_users:";
    private static final long TTL_HOURS = 24;

    private final StringRedisTemplate redisTemplate;
    private final NotificationSettingRepository notificationSettingRepository;
    private final ObjectMapper objectMapper;

    public NotificationCacheService(StringRedisTemplate redisTemplate, NotificationSettingRepository notificationSettingRepository) {
        this.redisTemplate = redisTemplate;
        this.notificationSettingRepository = notificationSettingRepository;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    /**
     * 1. 유저 알림 설정 및 DND 조회 (Look-Aside 캐싱)
     */
    public NotificationSettingResponse getCachedSetting(Long userId) {
        if (userId == null) return NotificationSettingResponse.from(null);

        String key = SETTING_KEY_PREFIX + userId;
        String cachedJson = redisTemplate.opsForValue().get(key);

        if (cachedJson != null) {
            try {
                return objectMapper.readValue(cachedJson, NotificationSettingResponse.class);
            } catch (JsonProcessingException e) {
                log.warn("Redis 알림 설정 파싱 실패 (userId: {})", userId, e);
            }
        }

        NotificationSetting setting = notificationSettingRepository.findByUserId(userId).orElse(null);
        NotificationSettingResponse response = NotificationSettingResponse.from(setting);

        if (setting != null) {
            updateCachedSetting(userId, setting);
        }

        return response;
    }

    /**
     * 2. 알림 설정 갱신 및 캐시 동기화 (Write-Through & Set Sync)
     */
    public void updateCachedSetting(Long userId, NotificationSetting setting) {
        if (userId == null || setting == null) return;

        // A. 유저 설정 캐시 업데이트
        String settingKey = SETTING_KEY_PREFIX + userId;
        NotificationSettingResponse response = NotificationSettingResponse.from(setting);
        try {
            redisTemplate.opsForValue().set(settingKey, objectMapper.writeValueAsString(response), TTL_HOURS, TimeUnit.HOURS);
        } catch (JsonProcessingException e) {
            log.warn("Redis 알림 설정 저장 실패 (userId: {})", userId, e);
        }

        // B. 알림 유형별 활성 유저 Set 갱신 (SADD / SREM)
        String userIdStr = String.valueOf(userId);
        boolean hasToken = setting.getFcmToken() != null && !setting.getFcmToken().trim().isEmpty();

        updateActiveSet("wishlist", userIdStr, Boolean.TRUE.equals(setting.getIsWishlistPushEnabled()) && hasToken);
        updateActiveSet("goldenhour", userIdStr, Boolean.TRUE.equals(setting.getIsGoldenHourPushEnabled()) && hasToken);
        updateActiveSet("community", userIdStr, Boolean.TRUE.equals(setting.getIsCommunityPushEnabled()) && hasToken);
    }

    private void updateActiveSet(String type, String userIdStr, boolean isActive) {
        String key = ACTIVE_USERS_KEY_PREFIX + type;
        if (isActive) {
            redisTemplate.opsForSet().add(key, userIdStr);
            redisTemplate.expire(key, TTL_HOURS, TimeUnit.HOURS);
        } else {
            redisTemplate.opsForSet().remove(key, userIdStr);
        }
    }

    /**
     * 3. 알림 유형별 활성 유저 ID 목록 반환 (Look-Aside)
     */
    public Set<Long> getActiveUserIds(String type) {
        String key = ACTIVE_USERS_KEY_PREFIX + type.toLowerCase();
        Set<String> members = redisTemplate.opsForSet().members(key);

        if (members != null && !members.isEmpty()) {
            return members.stream().map(Long::valueOf).collect(Collectors.toSet());
        }

        // Cache Miss 시 DB에서 쿼리하여 Redis Set 워밍업
        List<NotificationSetting> dbSettings = switch (type.toLowerCase()) {
            case "wishlist" -> notificationSettingRepository.findActiveWishlistSettingsWithToken();
            case "goldenhour" -> notificationSettingRepository.findActiveGoldenHourSettingsWithToken();
            case "community" -> notificationSettingRepository.findActiveCommunitySettingsWithToken();
            default -> List.of();
        };

        Set<Long> userIds = dbSettings.stream().map(NotificationSetting::getUserId).collect(Collectors.toSet());
        if (!userIds.isEmpty()) {
            String[] userIdArr = userIds.stream().map(String::valueOf).toArray(String[]::new);
            redisTemplate.opsForSet().add(key, userIdArr);
            redisTemplate.expire(key, TTL_HOURS, TimeUnit.HOURS);
        }

        return userIds;
    }

    /**
     * 4. 유저 캐시 무효화 (Evict)
     */
    public void evictUserSetting(Long userId) {
        if (userId == null) return;
        redisTemplate.delete(SETTING_KEY_PREFIX + userId);
        String userIdStr = String.valueOf(userId);
        redisTemplate.opsForSet().remove(ACTIVE_USERS_KEY_PREFIX + "wishlist", userIdStr);
        redisTemplate.opsForSet().remove(ACTIVE_USERS_KEY_PREFIX + "goldenhour", userIdStr);
        redisTemplate.opsForSet().remove(ACTIVE_USERS_KEY_PREFIX + "community", userIdStr);
    }
}
