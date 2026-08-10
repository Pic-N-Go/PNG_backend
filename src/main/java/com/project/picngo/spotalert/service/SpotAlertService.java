package com.project.picngo.spotalert.service;

import com.project.picngo.external.service.WeatherCacheService;
import com.project.picngo.notification.repository.NotificationSettingRepository;
import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.domain.SpotTag;
import com.project.picngo.spot.repository.SpotRepository;
import com.project.picngo.spot.repository.SpotTagRepository;
import com.project.picngo.spotalert.domain.SpotAlert;
import com.project.picngo.spotalert.domain.enums.TimeCondition;
import com.project.picngo.spotalert.domain.enums.WeatherCondition;
import com.project.picngo.spotalert.dto.*;
import com.project.picngo.spotalert.repository.SpotAlertRepository;
import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.SpotAlertErrorCode;
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
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SpotAlertService {

    private final SpotAlertRepository spotAlertRepository;
    private final WeatherCacheService weatherCacheService;
    private final UserRepository userRepository;
    private final SpotRepository spotRepository;
    private final SpotTagRepository spotTagRepository;
    private final NotificationSettingRepository notificationSettingRepository;
    private final WeatherMatchService weatherMatchService;

    public List<SpotAlertSettingResponse> getSpotAlerts(Long userId) {
        validateUserExists(userId);
        return spotAlertRepository.findAllByUserId(userId).stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    public SpotAlertSettingResponse getSpotAlertDetail(Long userId, Long spotId) {
        validateUserExists(userId);
        SpotAlert spotAlert = spotAlertRepository.findByUserIdAndSpotId(userId, spotId)
                .orElseThrow(() -> new CustomException(SpotAlertErrorCode.SPOT_ALERT_NOT_FOUND_OR_UNAUTHORIZED));
        return convertToResponse(spotAlert);
    }

    @Transactional
    public SpotAlertSettingResponse updateSpotAlertSettings(Long userId, Long spotId, SpotAlertSettingUpdateRequest request) {
        validateUserExists(userId);
        validateAlertTimingDays(request.alertTimingDays());

        SpotAlert spotAlert = spotAlertRepository.findByUserIdAndSpotId(userId, spotId)
                .orElseGet(() -> SpotAlert.builder()
                        .userId(userId)
                        .spotId(spotId)
                        .build());
        
        spotAlert.updateSettings(
                request.memo(),
                request.weatherConditions(),
                request.timeConditions(),
                request.airQualityCondition(),
                request.alertTimingDays(),
                request.isAlertEnabled()
        );

        SpotAlert saved = spotAlertRepository.save(spotAlert);
        return convertToResponse(saved);
    }

    @Transactional
    public SpotAlertSettingResponse updateSpotAlertActive(Long userId, Long spotId, SpotAlertActiveUpdateRequest request) {
        validateUserExists(userId);
        SpotAlert spotAlert = spotAlertRepository.findByUserIdAndSpotId(userId, spotId)
                .orElseThrow(() -> new CustomException(SpotAlertErrorCode.SPOT_ALERT_NOT_FOUND_OR_UNAUTHORIZED));

        spotAlert.updateActive(request.isAlertEnabled());
        return convertToResponse(spotAlert);
    }

    @Transactional
    public void deleteSpotAlert(Long userId, Long spotId) {
        validateUserExists(userId);
        SpotAlert spotAlert = spotAlertRepository.findByUserIdAndSpotId(userId, spotId)
                .orElseThrow(() -> new CustomException(SpotAlertErrorCode.SPOT_ALERT_NOT_FOUND_OR_UNAUTHORIZED));
        spotAlertRepository.delete(spotAlert);
    }


    private void validateUserExists(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new CustomException(UserErrorCode.USER_NOT_FOUND);
        }
    }

    // 알림 시점은 당일(0)/1일 전(1)/3일 전(3)만 허용
    private static final Set<Integer> ALLOWED_ALERT_TIMING_DAYS = Set.of(0, 1, 3);

    private void validateAlertTimingDays(Integer alertTimingDays) {
        if (alertTimingDays != null && !ALLOWED_ALERT_TIMING_DAYS.contains(alertTimingDays)) {
            throw new CustomException(SpotAlertErrorCode.INVALID_ALERT_TIMING);
        }
    }

    private SpotAlertSettingResponse convertToResponse(SpotAlert spotAlert) {
        Spot spot = spotRepository.findById(spotAlert.getSpotId()).orElse(null);
        String spotName = spot != null ? spot.getName() : "알 수 없는 스팟";
        String address = spot != null ? spot.getAddress() : "주소 미상";
        Integer photogenicScore = spot != null ? spot.getPhotogenicScore() : 0;
        
        List<String> tags = spotTagRepository.findBySpotId(spotAlert.getSpotId())
                .stream()
                .map(SpotTag::getTag)
                .collect(Collectors.toList());
                
        LocalTime dndStartTime = null;
        LocalTime dndEndTime = null;
        var notiSetting = notificationSettingRepository.findByUserId(spotAlert.getUserId()).orElse(null);
        if (notiSetting != null) {
            dndStartTime = notiSetting.getDndStartTime();
            dndEndTime = notiSetting.getDndEndTime();
        }

        List<SpotAlertSettingResponse.ExpectedMatchDayDto> expectedMatchDays = new ArrayList<>();
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
                    // 대표 날씨(화면 표시용): 낮 시간대(10/14/18) 중간값, 없으면 전체 중간값
                    List<com.project.picngo.external.dto.WeatherForecastResponse> daytime = dayForecasts.stream()
                        .filter(f -> f.time().equals("1000") || f.time().equals("1400") || f.time().equals("1800"))
                        .toList();
                    if (daytime.isEmpty()) daytime = dayForecasts;
                    repWeather = daytime.get(daytime.size() / 2).weatherStatus();

                    // 매치 판정: 알림 스케줄러와 동일한 공용 매칭 로직(시간대 반영)을 사용
                    Set<TimeCondition> timeConditions = spotAlert.getTimeConditions();
                    Set<WeatherCondition> weatherConditions = spotAlert.getWeatherConditions();
                    if (timeConditions == null || timeConditions.isEmpty()) {
                        isMatched = weatherMatchService.matchesAnyTime(forecasts, targetDateStr, weatherConditions);
                    } else {
                        isMatched = timeConditions.stream()
                            .anyMatch(tc -> weatherMatchService.matches(forecasts, targetDateStr, tc, weatherConditions));
                    }
                }
                
                expectedMatchDays.add(new SpotAlertSettingResponse.ExpectedMatchDayDto(
                    dayLabel,
                    target.format(DateTimeFormatter.ofPattern("MM/dd")),
                    repWeather,
                    isMatched
                ));
            }
        }
        
        return new SpotAlertSettingResponse(
                spotAlert.getSpotId(),
                spotName, 
                address,
                photogenicScore,
                tags,
                spotAlert.getMemo(),
                spotAlert.getWeatherConditions(),
                spotAlert.getTimeConditions(),
                spotAlert.getAirQualityCondition(),
                spotAlert.getIsActive(),
                spotAlert.getAlertTimingDays(),
                dndStartTime,
                dndEndTime,
                expectedMatchDays
        );
    }
}
