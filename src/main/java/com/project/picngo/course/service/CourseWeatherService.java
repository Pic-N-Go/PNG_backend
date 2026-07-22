package com.project.picngo.course.service;

import com.project.picngo.course.domain.Course;
import com.project.picngo.course.domain.CourseSpot;
import com.project.picngo.course.dto.CourseWeatherResponse;
import com.project.picngo.course.dto.WeatherDetail;
import com.project.picngo.course.repository.CourseRepository;
import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.CourseErrorCode;
import com.project.picngo.external.AirQualityClient;
import com.project.picngo.external.dto.AirQualityResponse.Item;
import com.project.picngo.external.dto.GoldenHourResponse;
import com.project.picngo.external.dto.WeatherForecastResponse;
import com.project.picngo.external.service.WeatherCacheService;
import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.repository.SpotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseWeatherService {

    private final CourseRepository courseRepository;
    private final SpotRepository spotRepository;
    private final WeatherCacheService weatherCacheService;
    private final AirQualityClient airQualityClient;

    public List<CourseWeatherResponse> getCourseWeather(Long courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new CustomException(CourseErrorCode.COURSE_NOT_FOUND));

        if (course.getStartDate() == null) {
            return List.of();
        }

        List<CourseWeatherResponse> weatherResponses = new ArrayList<>();
        Map<Integer, List<CourseSpot>> spotsByDay = course.getCourseSpots().stream()
                .collect(Collectors.groupingBy(CourseSpot::getDayNumber));

        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd");

        for (Map.Entry<Integer, List<CourseSpot>> entry : spotsByDay.entrySet()) {
            Integer dayNumber = entry.getKey();
            List<CourseSpot> daySpots = entry.getValue();
            
            if (daySpots.isEmpty()) continue;

            // 일차별 첫 번째 스팟을 날씨 조회 타겟으로 선정
            daySpots.sort((a, b) -> a.getSequenceOrder().compareTo(b.getSequenceOrder()));
            CourseSpot targetCourseSpot = daySpots.get(0);
            
            Spot targetSpot = spotRepository.findById(targetCourseSpot.getSpotId()).orElse(null);
            if (targetSpot == null) continue;

            // 해당 일차의 방문 날짜 계산
            LocalDate visitDate = course.getStartDate().plusDays(dayNumber - 1);
            String dateString = visitDate.format(dateFormatter);

            // 날씨 조회 (캐시 활용)
            WeatherDetail morning = new WeatherDetail("데이터 없음", null);
            WeatherDetail afternoon = new WeatherDetail("데이터 없음", null);
            WeatherDetail evening = new WeatherDetail("데이터 없음", null);
            try {
                List<WeatherForecastResponse> forecastList = weatherCacheService.getCached7DayForecast(
                        targetSpot.getLatitude(), targetSpot.getLongitude(), dateString
                );
                
                if (forecastList != null && !forecastList.isEmpty()) {
                    for (WeatherForecastResponse f : forecastList) {
                        if (dateString.equals(f.date())) {
                            if ("1000".equals(f.time())) {
                                morning = new WeatherDetail(f.weatherStatus(), f.temperature() != null ? f.temperature().intValue() : null);
                            } else if ("1400".equals(f.time())) {
                                afternoon = new WeatherDetail(f.weatherStatus(), f.temperature() != null ? f.temperature().intValue() : null);
                            } else if ("1800".equals(f.time())) {
                                evening = new WeatherDetail(f.weatherStatus(), f.temperature() != null ? f.temperature().intValue() : null);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("코스 날씨 조회 실패 (courseId: {}, dayNumber: {})", courseId, dayNumber, e);
                morning = new WeatherDetail("에러: API 실패", null);
                afternoon = new WeatherDetail("에러: API 실패", null);
                evening = new WeatherDetail("에러: API 실패", null);
            }

            // 골든아워 조회 (캐시 활용)
            String sunsetTime = null;
            String goldenHourEvening = null;
            try {
                GoldenHourResponse goldenHour = weatherCacheService.getCachedGoldenHour(
                        targetSpot.getLatitude(), targetSpot.getLongitude(), dateString
                );
                if (goldenHour != null) {
                    sunsetTime = goldenHour.sunsetTime();
                    goldenHourEvening = goldenHour.goldenHourEvening();
                }
            } catch (Exception e) {
                log.warn("코스 골든아워 조회 실패 (courseId: {}, dayNumber: {})", courseId, dayNumber, e);
            }

            // 미세먼지 조회 (가까운 시일 내의 일정만 조회, 최대 3일 이내)
            String fineDustStatus = "데이터 없음";
            long daysUntilVisit = ChronoUnit.DAYS.between(LocalDate.now(), visitDate);
            
            if (daysUntilVisit >= 0 && daysUntilVisit <= 3) {
                try {
                    String region = extractRegion(targetSpot.getAddress());
                    Item air = weatherCacheService.getCachedAirQuality(region);
                    fineDustStatus = getFineDustStatus(air);
                } catch (Exception e) {
                    log.warn("코스 미세먼지 조회 실패 (courseId: {}, dayNumber: {})", courseId, dayNumber, e);
                }
            }

            weatherResponses.add(new CourseWeatherResponse(
                    dayNumber,
                    visitDate,
                    targetSpot.getId(),
                    targetSpot.getName(),
                    morning,
                    afternoon,
                    evening,
                    sunsetTime,
                    goldenHourEvening,
                    fineDustStatus
            ));
        }

        return weatherResponses;
    }

    private String getFineDustStatus(Item air) {
        if (air == null || air.pm10Value() == null || air.pm10Value().equals("-")) {
            return "데이터 없음";
        }
        String grade = air.pm10Grade() != null ? air.pm10Grade() : "";
        if (grade.isEmpty()) {
            try {
                int val = Integer.parseInt(air.pm10Value());
                grade = val <= 30 ? "1" : val <= 80 ? "2" : val <= 150 ? "3" : "4";
            } catch (NumberFormatException e) {
                return "데이터 없음";
            }
        }
        return switch (grade) {
            case "1" -> "좋음";
            case "2" -> "보통";
            case "3" -> "나쁨";
            default  -> "매우나쁨";
        };
    }

    private String extractRegion(String address) {
        if (address == null) return "";
        String first = address.split(" ")[0];
        return first.replace("특별시", "").replace("광역시", "").replace("특별자치시", "").replace("특별자치도", "");
    }
}
