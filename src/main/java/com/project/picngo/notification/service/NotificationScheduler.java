package com.project.picngo.notification.service;

import com.project.picngo.external.WeatherClient;
import com.project.picngo.external.dto.WeatherForecastResponse;
import com.project.picngo.external.dto.GoldenHourResponse;
import com.project.picngo.external.service.WeatherCacheService;
import com.project.picngo.notification.domain.NotificationSetting;
import com.project.picngo.notification.repository.NotificationSettingRepository;
import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.repository.SpotRepository;
import com.project.picngo.spotalert.domain.SpotAlert;
import com.project.picngo.spotalert.domain.enums.TimeCondition;
import com.project.picngo.spotalert.domain.enums.WeatherCondition;
import com.project.picngo.spotalert.repository.SpotAlertRepository;
import com.project.picngo.spotalert.service.WeatherMatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationScheduler {

    private final NotificationSettingRepository notificationSettingRepository;
    private final SpotAlertRepository spotAlertRepository;
    private final SpotRepository spotRepository;
    private final WeatherCacheService weatherCacheService;
    private final NotificationService notificationService;
    private final NotificationCacheService notificationCacheService;
    private final ThreadPoolTaskExecutor weatherWarmupExecutor;
    private final WeatherMatchService weatherMatchService;

    // [A. 고정 시간대 스케줄러]
    // 해당 시간에 스케줄러가 돌아가며, 조건이 맞는 스팟들에 대해 알림 발송

    // 22시에 새벽 알림 발송
    @Scheduled(cron = "0 0 22 * * *")
    public void scheduleDawnNotification() {
        log.info("새벽(DAWN) 알림 스케줄러 실행...");
        processFixedTimeNotification(TimeCondition.DAWN);
    }

    // 4시에 오전 알림 발송
    @Scheduled(cron = "0 0 4 * * *")
    public void scheduleMorningNotification() {
        log.info("오전(MORNING) 알림 스케줄러 실행...");
        processFixedTimeNotification(TimeCondition.MORNING);
    }

    // 11시에 오후 알림 발송
    @Scheduled(cron = "0 0 11 * * *")
    public void scheduleAfternoonNotification() {
        log.info("오후(AFTERNOON) 알림 스케줄러 실행...");
        processFixedTimeNotification(TimeCondition.AFTERNOON);
    }

    // 16시에 야간 알림 발송
    @Scheduled(cron = "0 0 16 * * *")
    public void scheduleNightNotification() {
        log.info("야간(NIGHT) 알림 스케줄러 실행...");
        processFixedTimeNotification(TimeCondition.NIGHT);
    }

    // [B. 동적 시간대(골든아워) 스케줄러]

    // 일출 스케줄러 (04:00 ~ 08:00 사이 매 10분마다 실행)
    @Scheduled(cron = "0 0/10 4-8 * * *")
    public void scheduleSunriseNotification() {
        log.info("일출(SUNRISE) 임박 알림 스케줄러 실행...");
        processGoldenHourNotification(TimeCondition.SUNRISE);
    }

    // 일몰 스케줄러 (16:00 ~ 20:00 사이 매 10분마다 실행)
    @Scheduled(cron = "0 0/10 16-20 * * *")
    public void scheduleSunsetNotification() {
        log.info("일몰(SUNSET) 임박 알림 스케줄러 실행...");
        processGoldenHourNotification(TimeCondition.SUNSET);
    }

    // --- 공통 로직 ---

    // 고정된 시간대 알림 처리 (출사알림 날씨 매칭)
    private void processFixedTimeNotification(TimeCondition timeCondition) {
        long startTime = System.currentTimeMillis();
        List<Long> activeUserIds = getActiveSpotAlertUserIds();
        if (activeUserIds.isEmpty()) return;

        // 1. N+1 문제 해결: activeUserIds 목록으로 출사알림 및 조건들을 단 1번의 쿼리로 일괄 배치 조회
        List<SpotAlert> allSpotAlerts = spotAlertRepository.findAllByUserIdInAndIsActiveTrue(activeUserIds);

        // 2. N+1 문제 해결: 스팟 ID 수집 후 단 1번의 IN 쿼리로 스팟 정보를 인메모리 맵에 일괄 로딩
        List<Long> spotIds = allSpotAlerts.stream().map(SpotAlert::getSpotId).distinct().toList();
        Map<Long, Spot> spotMap = spotRepository.findByIdIn(spotIds).stream()
                .collect(Collectors.toMap(Spot::getId, s -> s));

        // 3. 병목 해결: 매칭 대상 스팟의 날씨 예보를 '고유 좌표 단위'로 병렬 사전 워밍업.
        //    이후 매칭 루프의 getCached7DayForecast()는 전부 캐시 히트가 되어 외부 API 직렬 호출이 사라진다.
        prewarmForecastCache(allSpotAlerts, spotMap, timeCondition);

        int matchedCount = 0;
        for (SpotAlert spotAlert : allSpotAlerts) {
            if (spotAlert.getTimeConditions().contains(timeCondition)) {
                Spot spot = spotMap.get(spotAlert.getSpotId());
                if (spot != null && checkWeatherAndNotify(spotAlert.getUserId(), spotAlert, spot, timeCondition)) {
                    matchedCount++;
                }
            }
        }
        long endTime = System.currentTimeMillis();
        log.info("\n==================================================" +
                "\n⏱️ [{}] 스케줄러 실행 및 푸시 발송 완료 (총 소요시간: {} ms / {} 건 발송)" +
                "\n==================================================", timeCondition, (endTime - startTime), matchedCount);
    }

    /**
     * 매칭 대상 스팟들의 7일 예보를 사전에 병렬로 조회하여 Redis 캐시를 채운다.
     * <p>
     * 날씨 예보 캐시 키는 좌표를 소수점 1자리로 반올림한 값({@code %.1f_%.1f})만 사용하므로,
     * 그 키 기준으로 중복을 먼저 제거하여 '고유 좌표당 정확히 1번'만 외부 API를 호출한다.
     * (중복 제거 후 병렬 호출이므로 동일 좌표를 동시에 여러 번 조회하는 캐시 스탬피드도 방지된다.)
     */
    private void prewarmForecastCache(List<SpotAlert> allSpotAlerts, Map<Long, Spot> spotMap, TimeCondition timeCondition) {
        String todayStr = LocalDate.now(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        // 시간 조건이 일치하는 스팟만, 캐시 키(반올림 좌표) 기준으로 중복 제거
        Map<String, Spot> distinctByCacheKey = new LinkedHashMap<>();
        for (SpotAlert spotAlert : allSpotAlerts) {
            if (!spotAlert.getTimeConditions().contains(timeCondition)) continue;
            Spot spot = spotMap.get(spotAlert.getSpotId());
            if (spot == null || spot.getLatitude() == null || spot.getLongitude() == null) continue;
            String cacheKey = String.format(Locale.US, "%.1f_%.1f", spot.getLatitude(), spot.getLongitude());
            distinctByCacheKey.putIfAbsent(cacheKey, spot);
        }

        if (distinctByCacheKey.isEmpty()) return;

        long warmStart = System.currentTimeMillis();
        List<CompletableFuture<Void>> futures = distinctByCacheKey.values().stream()
                .map(spot -> CompletableFuture.runAsync(() -> {
                    try {
                        // 캐시에 없으면 이 시점에 외부 API를 호출하여 Redis에 적재된다.
                        weatherCacheService.getCached7DayForecast(spot.getLatitude(), spot.getLongitude(), todayStr);
                    } catch (Exception e) {
                        log.warn("날씨 예보 사전 워밍업 실패 (spotId: {})", spot.getId(), e);
                    }
                }, weatherWarmupExecutor))
                .toList();

        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(60, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("날씨 예보 사전 워밍업 대기 중 타임아웃/오류 발생 (일부 좌표는 매칭 루프에서 개별 조회될 수 있음)", e);
        }

        log.info("🔥 날씨 예보 사전 병렬 워밍업 완료 (고유 좌표 {}개, 소요: {} ms)",
                distinctByCacheKey.size(), (System.currentTimeMillis() - warmStart));
    }

    // 매일 변하는 자연 현상(일출/일몰)의 타이밍을 실시간으로 계산하는 타이머 (골든아워)
    private void processGoldenHourNotification(TimeCondition timeCondition) {
        long startTime = System.currentTimeMillis();
        List<Long> activeUserIds = getActiveGoldenHourUserIds();
        if (activeUserIds.isEmpty()) return;

        // 1. N+1 해결: 출사알림/스팟을 각각 1번의 배치 쿼리로 일괄 조회
        List<SpotAlert> allSpotAlerts = spotAlertRepository.findAllByUserIdInAndIsActiveTrue(activeUserIds);
        List<Long> spotIds = allSpotAlerts.stream().map(SpotAlert::getSpotId).distinct().toList();
        Map<Long, Spot> spotMap = spotRepository.findByIdIn(spotIds).stream()
                .collect(Collectors.toMap(Spot::getId, s -> s));

        // 2. 병목 해결: 고유 (좌표, 타겟일) 단위로 골든아워 예보를 병렬 사전 워밍업
        prewarmGoldenHourCache(allSpotAlerts, spotMap, timeCondition);

        // 3. 유저별 최대 1건만 발송 (기존 break 시맨틱 유지) → 유저 단위로 그룹핑 후 첫 매칭에서 중단
        Map<Long, List<SpotAlert>> byUser = allSpotAlerts.stream()
                .collect(Collectors.groupingBy(SpotAlert::getUserId));

        int matchedCount = 0;
        for (Map.Entry<Long, List<SpotAlert>> entry : byUser.entrySet()) {
            Long userId = entry.getKey();
            for (SpotAlert spotAlert : entry.getValue()) {
                if (!spotAlert.getTimeConditions().contains(timeCondition)) continue;
                Spot spot = spotMap.get(spotAlert.getSpotId());
                if (spot == null) continue;
                if (checkGoldenHourAndNotify(userId, spotAlert, spot, timeCondition)) {
                    matchedCount++;
                    break; // 동일 유저 중복 골든아워 알림 폭탄 방지 (1건 발송 후 다음 유저)
                }
            }
        }
        long endTime = System.currentTimeMillis();
        log.info("\n==================================================" +
                "\n⏱️ [{}] 골든아워 스케줄러 실행 완료 (총 소요시간: {} ms / {} 건 발송)" +
                "\n==================================================", timeCondition, (endTime - startTime), matchedCount);
    }

    /**
     * 골든아워(일출/일몰) 예보를 고유 (반올림 좌표, 타겟일) 단위로 병렬 사전 조회하여 Redis 캐시를 채운다.
     * 골든아워 캐시 키는 정수 반올림 좌표(%.0f) + 타겟일을 사용하므로 그 단위로 중복을 제거한다.
     */
    private void prewarmGoldenHourCache(List<SpotAlert> allSpotAlerts, Map<Long, Spot> spotMap, TimeCondition timeCondition) {
        Map<String, GoldenHourWarmTarget> distinct = new LinkedHashMap<>();
        for (SpotAlert spotAlert : allSpotAlerts) {
            if (!spotAlert.getTimeConditions().contains(timeCondition)) continue;
            Spot spot = spotMap.get(spotAlert.getSpotId());
            if (spot == null || spot.getLatitude() == null || spot.getLongitude() == null) continue;
            int dDay = spotAlert.getAlertTimingDays() != null ? spotAlert.getAlertTimingDays() : 0;
            String targetDate = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(dDay).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            String cacheKey = String.format(Locale.US, "%.0f_%.0f_%s", spot.getLatitude(), spot.getLongitude(), targetDate);
            distinct.putIfAbsent(cacheKey, new GoldenHourWarmTarget(spot.getLatitude(), spot.getLongitude(), targetDate, spot.getId()));
        }

        if (distinct.isEmpty()) return;

        long warmStart = System.currentTimeMillis();
        List<CompletableFuture<Void>> futures = distinct.values().stream()
                .map(t -> CompletableFuture.runAsync(() -> {
                    try {
                        weatherCacheService.getCachedGoldenHour(t.lat(), t.lng(), t.date());
                    } catch (Exception e) {
                        log.warn("골든아워 사전 워밍업 실패 (spotId: {})", t.spotId(), e);
                    }
                }, weatherWarmupExecutor))
                .toList();

        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(60, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("골든아워 사전 워밍업 대기 중 타임아웃/오류 발생 (일부는 매칭 루프에서 개별 조회될 수 있음)", e);
        }

        log.info("🔥 골든아워 사전 병렬 워밍업 완료 (고유 좌표·타겟일 {}개, 소요: {} ms)",
                distinct.size(), (System.currentTimeMillis() - warmStart));
    }

    // 단일 출사알림의 골든아워 임박 여부 확인 후 발송. 발송했으면 true.
    private boolean checkGoldenHourAndNotify(Long userId, SpotAlert spotAlert, Spot spot, TimeCondition timeCondition) {
        try {
            Double lat = spot.getLatitude();
            Double lng = spot.getLongitude();

            int dDay = spotAlert.getAlertTimingDays() != null ? spotAlert.getAlertTimingDays() : 0;
            String targetDate = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(dDay).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

            GoldenHourResponse gh = weatherCacheService.getCachedGoldenHour(lat, lng, targetDate);

            String targetUtcTimeStr = timeCondition == TimeCondition.SUNRISE ? gh.sunriseTime() : gh.sunsetTime();
            if (targetUtcTimeStr == null || targetUtcTimeStr.isEmpty()) return false;

            // ISO 8601 parsing e.g., 2015-05-21T05:05:35+00:00
            java.time.ZonedDateTime targetUtc = java.time.ZonedDateTime.parse(targetUtcTimeStr, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            java.time.ZonedDateTime targetKst = targetUtc.withZoneSameInstant(ZoneId.of("Asia/Seoul"));

            java.time.ZonedDateTime now = java.time.ZonedDateTime.now(ZoneId.of("Asia/Seoul"));

            // 스케줄러가 일치해야 할 '오늘의 알림 타겟 시간'을 D-Day의 대상 시간과 동일한 시간으로 간주
            java.time.ZonedDateTime alertTime = now.withHour(targetKst.getHour()).withMinute(targetKst.getMinute()).withSecond(0).withNano(0);

            // 현재 시간이 알림 발송 시간(alertTime)의 반경 10분 이내인지 확인 (스케줄러가 10분마다 도므로)
            long diffMinutes = java.time.Duration.between(now, alertTime).toMinutes();
            if (Math.abs(diffMinutes) <= 5) {
                log.info("유저 {} 의 스팟 {} 에 대해 {} 골든아워 임박 알림 발송 조건 충족! (D-{})", userId, spot.getId(), timeCondition, dDay);
                String dayStr = dDay == 0 ? "오늘" : dDay + "일 뒤";
                String title = "🌅 골든아워 알림";
                String content = String.format("%s %s %s 시간은 %02d시 %02d분 입니다.", dayStr, spot.getName(), timeCondition == TimeCondition.SUNRISE ? "일출" : "일몰", targetKst.getHour(), targetKst.getMinute());
                // 멱등키: 같은 날·같은 스팟·같은 일출/일몰 알림 중복 방지
                String ghDedupeKey = String.format("GOLDEN_HOUR:%d:%d:%s:%s", userId, spot.getId(), targetDate, timeCondition);
                notificationService.sendPushNotification(userId, "GOLDEN_HOUR", title, content, "/spot-alerts/" + spot.getId(), spot.getId(), ghDedupeKey);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("골든아워 확인 중 오류 발생 (유저: {}, 스팟: {})", userId, spotAlert.getSpotId(), e);
            return false;
        }
    }

    // 골든아워 워밍업 대상 (중복 제거된 고유 좌표·타겟일)
    private record GoldenHourWarmTarget(Double lat, Double lng, String date, Long spotId) {}

    // 날씨 및 미세먼지 조건 확인 후 알림 발송. 실제로 발송했으면 true.
    private boolean checkWeatherAndNotify(Long userId, SpotAlert spotAlert, Spot spot, TimeCondition timeCondition) {
        try {
            Double lat = spot.getLatitude();
            Double lng = spot.getLongitude();
            // 좌표가 없으면 예보 조회 불가 → 프리워밍업과 동일하게 스킵
            if (lat == null || lng == null) {
                return false;
            }

            int dDay = spotAlert.getAlertTimingDays() != null ? spotAlert.getAlertTimingDays() : 0;
            // DAWN(새벽)은 밤 22시에 트리거되므로 대상 새벽은 '다음 날'이다. 그래서 하루를 더해 보정
            int targetOffset = timeCondition == TimeCondition.DAWN ? dDay + 1 : dDay;
            String todayStr = LocalDate.now(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String targetDateStr = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(targetOffset).format(DateTimeFormatter.ofPattern("yyyyMMdd"));

            // 캐싱된 7일 예보 조회
            List<WeatherForecastResponse> combinedForecast = weatherCacheService.getCached7DayForecast(lat, lng, todayStr);

            Set<WeatherCondition> userConditions = spotAlert.getWeatherConditions();
            if (userConditions == null || userConditions.isEmpty()) {
                return false;
            }

            // 촬영 시간대(timeCondition)에 해당하는 예보 슬롯으로 날씨 조건 일치 여부 판정 (공용 매칭 로직)
            boolean isMatched = weatherMatchService.matches(combinedForecast, targetDateStr, timeCondition, userConditions);

            if (isMatched) {
                // 미세먼지 조건 필터링
                boolean isAirQualityMatched = true; 
                com.project.picngo.spotalert.domain.enums.AirQualityCondition aqCondition = spotAlert.getAirQualityCondition();
                
                if (aqCondition != null && aqCondition != com.project.picngo.spotalert.domain.enums.AirQualityCondition.NONE) {
                    try {
                        String sidoName = com.project.picngo.external.SidoNameMapper.normalize(spot.getAddress());
                        if (sidoName == null) sidoName = "서울"; // 주소 없음/짧음 → 기존 기본값 유지

                        com.project.picngo.external.dto.AirQualityResponse.Item aqItem = weatherCacheService.getCachedAirQuality(sidoName);
                        
                        if (aqItem != null && aqItem.pm10Grade() != null) {
                            int pm10Grade = Integer.parseInt(aqItem.pm10Grade());
                            if (aqCondition == com.project.picngo.spotalert.domain.enums.AirQualityCondition.GOOD && pm10Grade > 1) {
                                isAirQualityMatched = false;
                            } else if (aqCondition == com.project.picngo.spotalert.domain.enums.AirQualityCondition.NORMAL_OR_BETTER && pm10Grade > 2) {
                                isAirQualityMatched = false;
                            }
                        }
                    } catch (Exception e) {
                        log.warn("미세먼지 확인 중 오류 발생, 알림 발송은 진행합니다.", e);
                    }
                }

                if (isAirQualityMatched) {
                    log.info("유저 {} 의 스팟 {} 에 대한 날씨 및 미세먼지 조건이 일치합니다! ({} / D-{})", userId, spot.getId(), timeCondition, dDay);
                    // 시간대별로 메시지를 구분해 유저가 어느 촬영 시간대 알림인지 알 수 있게 한다.
                    String whenStr = targetOffset == 0 ? "오늘" : (targetOffset == 1 ? "내일" : targetOffset + "일 뒤");
                    String timeLabel = timeConditionLabel(timeCondition);
                    String title = String.format("☁️ %s 날씨 조건 알림", timeLabel);
                    String content = String.format("%s %s %s의 날씨가 설정하신 조건과 일치할 예정입니다!", whenStr, timeLabel, spot.getName());
                    // 멱등키: 같은 날·같은 스팟이라도 촬영 시간대별로 별개 알림 → timeCondition까지 포함
                    String dedupeKey = String.format("WEATHER_MATCH:%d:%d:%s:%s", userId, spot.getId(), targetDateStr, timeCondition);
                    notificationService.sendPushNotification(userId, "WEATHER_MATCH", title, content, "/spot-alerts/" + spot.getId(), spot.getId(), dedupeKey);
                    return true;
                }
            }
            return false;

        } catch (Exception e) {
            log.error("유저 {} 의 출사알림 체크 중 오류 발생", userId, e);
            return false;
        }
    }

    // 촬영 시간대 → 사용자 노출용 한글 라벨 (알림 메시지 구분용)
    private String timeConditionLabel(TimeCondition timeCondition) {
        return switch (timeCondition) {
            case DAWN -> "새벽";
            case MORNING -> "오전";
            case AFTERNOON -> "오후";
            case NIGHT -> "야간";
            case SUNRISE -> "일출";
            case SUNSET -> "일몰";
            default -> "";
        };
    }

    private List<Long> getActiveSpotAlertUserIds() {
        Set<Long> userIds = notificationCacheService.getActiveUserIds("spotalert");
        return userIds.stream()
                .filter(userId -> {
                    com.project.picngo.notification.dto.NotificationSettingResponse setting = notificationCacheService.getCachedSetting(userId);
                    return setting == null || !setting.isDndActive();
                })
                .toList();
    }

    private List<Long> getActiveGoldenHourUserIds() {
        Set<Long> userIds = notificationCacheService.getActiveUserIds("goldenhour");
        return userIds.stream()
                .filter(userId -> {
                    com.project.picngo.notification.dto.NotificationSettingResponse setting = notificationCacheService.getCachedSetting(userId);
                    return setting == null || !setting.isDndActive();
                })
                .toList();
    }

    // TODO: 운영 배포 전 또는 프론트엔드 알림 테스트 완료 후 테스트용 수동 스케줄러 강제 실행 메서드 삭제 필요
    public void triggerAllSchedulersManually() {
        log.info("테스트용 수동 스케줄러 강제 실행 (오전 날씨 알림 1회)...");
        processFixedTimeNotification(TimeCondition.MORNING);
        log.info("테스트용 수동 스케줄러 강제 실행 완료.");
    }
}
