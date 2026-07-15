package com.project.picngo.wishlist.service;

import com.project.picngo.external.service.WeatherCacheService;
import com.project.picngo.notification.repository.NotificationSettingRepository;
import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.domain.SpotTag;
import com.project.picngo.spot.repository.SpotRepository;
import com.project.picngo.spot.repository.SpotTagRepository;
import com.project.picngo.wishlist.domain.Wishlist;
import com.project.picngo.wishlist.dto.*;
import com.project.picngo.wishlist.repository.WishlistRepository;
import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.WishlistErrorCode;
import com.project.picngo.common.exception.code.UserErrorCode;
import com.project.picngo.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WishlistService {

    private final WishlistRepository wishlistRepository;
    private final WeatherCacheService weatherCacheService;
    private final UserRepository userRepository;
    private final SpotRepository spotRepository;
    private final SpotTagRepository spotTagRepository;
    private final NotificationSettingRepository notificationSettingRepository;

    public List<WishlistSettingResponse> getWishlists(Long userId) {
        validateUserExists(userId);
        return wishlistRepository.findAllByUserId(userId).stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    public WishlistSettingResponse getWishlistDetail(Long userId, Long spotId) {
        validateUserExists(userId);
        Wishlist wishlist = wishlistRepository.findByUserIdAndSpotId(userId, spotId)
                .orElseThrow(() -> new CustomException(WishlistErrorCode.WISHLIST_NOT_FOUND_OR_UNAUTHORIZED));
        return convertToResponse(wishlist);
    }

    @Transactional
    public WishlistSettingResponse updateWishlistSettings(Long userId, Long spotId, WishlistSettingUpdateRequest request) {
        validateUserExists(userId);
        
        Wishlist wishlist = wishlistRepository.findByUserIdAndSpotId(userId, spotId)
                .orElseGet(() -> Wishlist.builder()
                        .userId(userId)
                        .spotId(spotId)
                        .build());
        
        wishlist.updateSettings(
                request.memo(),
                request.weatherConditions(),
                request.timeConditions(),
                request.airQualityCondition(),
                request.alertTimingDays(),
                request.isAlertEnabled()
        );

        Wishlist saved = wishlistRepository.save(wishlist);
        return convertToResponse(saved);
    }

    @Transactional
    public void deleteWishlist(Long userId, Long spotId) {
        validateUserExists(userId);
        Wishlist wishlist = wishlistRepository.findByUserIdAndSpotId(userId, spotId)
                .orElseThrow(() -> new CustomException(WishlistErrorCode.WISHLIST_NOT_FOUND_OR_UNAUTHORIZED));
        wishlistRepository.delete(wishlist);
    }

    private void validateUserExists(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new CustomException(UserErrorCode.USER_NOT_FOUND);
        }
    }

    private WishlistSettingResponse convertToResponse(Wishlist wishlist) {
        Spot spot = spotRepository.findById(wishlist.getSpotId()).orElse(null);
        String spotName = spot != null ? spot.getName() : "알 수 없는 스팟";
        String address = spot != null ? spot.getAddress() : "주소 미상";
        Integer photogenicScore = spot != null ? spot.getPhotogenicScore() : 0;
        
        List<String> tags = spotTagRepository.findBySpotId(wishlist.getSpotId())
                .stream()
                .map(SpotTag::getTag)
                .collect(Collectors.toList());
                
        LocalTime dndStartTime = null;
        LocalTime dndEndTime = null;
        var notiSetting = notificationSettingRepository.findByUserId(wishlist.getUserId()).orElse(null);
        if (notiSetting != null) {
            dndStartTime = notiSetting.getDndStartTime();
            dndEndTime = notiSetting.getDndEndTime();
        }

        List<WishlistSettingResponse.ExpectedMatchDayDto> expectedMatchDays = new ArrayList<>();
        if (spot != null) {
            String todayStr = LocalDate.now(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            List<com.project.picngo.external.dto.WeatherForecastResponse> forecasts = weatherCacheService.getCached7DayForecast(spot.getLatitude(), spot.getLongitude(), todayStr);
            
            Map<String, List<com.project.picngo.external.dto.WeatherForecastResponse>> byDate = forecasts.stream()
                    .collect(Collectors.groupingBy(com.project.picngo.external.dto.WeatherForecastResponse::date));
            
            LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
            for (int i = 0; i <= 7; i++) {
                LocalDate target = today.plusDays(i);
                String targetDateStr = target.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
                List<com.project.picngo.external.dto.WeatherForecastResponse> dayForecasts = byDate.getOrDefault(targetDateStr, List.of());
                
                if (dayForecasts.isEmpty() && i > 0) continue; 
                
                String dayLabel;
                if (i == 0) {
                    dayLabel = "오늘";
                } else if (i == 1) {
                    dayLabel = "내일";
                } else {
                    dayLabel = target.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.KOREAN);
                }
                
                String repWeather = "CLEAR";
                boolean isMatched = false;
                
                if (!dayForecasts.isEmpty()) {
                    List<com.project.picngo.external.dto.WeatherForecastResponse> daytime = dayForecasts.stream()
                        .filter(f -> f.time().equals("1000") || f.time().equals("1400") || f.time().equals("1800"))
                        .toList();
                        
                    if (daytime.isEmpty()) daytime = dayForecasts; 
                    
                    repWeather = daytime.get(daytime.size() / 2).weatherStatus(); 
                    
                    if (wishlist.getWeatherConditions() != null && wishlist.getWeatherConditions().contains(com.project.picngo.wishlist.domain.enums.WeatherCondition.NONE)) {
                        isMatched = true;
                    } else if (wishlist.getWeatherConditions() != null) {
                        for (var f : daytime) {
                            try {
                                if (wishlist.getWeatherConditions().contains(com.project.picngo.wishlist.domain.enums.WeatherCondition.valueOf(f.weatherStatus()))) {
                                    isMatched = true;
                                    repWeather = f.weatherStatus(); // 매칭된 날씨를 대표로 표기
                                    break;
                                }
                            } catch (IllegalArgumentException e) {
                                log.warn("알 수 없는 기상청 날씨 상태 수신 (스킵 처리): {}", f.weatherStatus());
                            }
                        }
                    }
                }
                
                expectedMatchDays.add(new WishlistSettingResponse.ExpectedMatchDayDto(
                    dayLabel,
                    target.format(DateTimeFormatter.ofPattern("MM/dd")),
                    repWeather,
                    isMatched
                ));
            }
        }
        
        return new WishlistSettingResponse(
                wishlist.getSpotId(),
                spotName, 
                address,
                photogenicScore,
                tags,
                wishlist.getMemo(),
                wishlist.getWeatherConditions(),
                wishlist.getTimeConditions(),
                wishlist.getAirQualityCondition(),
                wishlist.getIsActive(),
                wishlist.getAlertTimingDays(),
                dndStartTime,
                dndEndTime,
                expectedMatchDays
        );
    }
}
