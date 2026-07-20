package com.project.picngo.course.service;

import com.project.picngo.course.domain.Course;
import com.project.picngo.course.domain.CourseSpot;
import com.project.picngo.course.dto.CourseWeatherResponse;
import com.project.picngo.course.repository.CourseRepository;
import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.CourseErrorCode;
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
            String weatherStatus = "알 수 없음";
            Integer temperature = null;
            try {
                List<WeatherForecastResponse> forecastList = weatherCacheService.getCached7DayForecast(
                        targetSpot.getLatitude(), targetSpot.getLongitude(), dateString
                );
                
                if (forecastList != null && !forecastList.isEmpty()) {
                    WeatherForecastResponse forecast = forecastList.get(0);
                    weatherStatus = forecast.weatherStatus();
                    if (forecast.temperature() != null) {
                        temperature = forecast.temperature().intValue();
                    }
                }
            } catch (Exception e) {
                log.warn("코스 날씨 조회 실패 (courseId: {}, dayNumber: {})", courseId, dayNumber, e);
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

            weatherResponses.add(new CourseWeatherResponse(
                    dayNumber,
                    visitDate,
                    targetSpot.getId(),
                    targetSpot.getName(),
                    weatherStatus,
                    temperature,
                    sunsetTime,
                    goldenHourEvening
            ));
        }

        return weatherResponses;
    }
}
