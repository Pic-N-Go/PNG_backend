package com.project.picngo.course.service;

import com.project.picngo.course.dto.CourseSpotAddRequest;
import com.project.picngo.course.dto.CourseSpotOrderUpdateRequest;
import com.project.picngo.course.dto.CourseSpotResponse;
import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.repository.SpotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CourseFacade {

    private final CourseService courseService;
    private final RouteCacheService routeCacheService;
    private final SpotRepository spotRepository;

    public CourseSpotResponse addCourseSpot(Long userId, Long courseId, CourseSpotAddRequest request) {
        CourseSpotResponse response = courseService.addCourseSpotInternal(userId, courseId, request);
        recalculateTravelTimesForDay(courseId, request.dayNumber());
        return response;
    }

    public void removeCourseSpot(Long userId, Long courseId, Long spotId) {
        Integer dayNumber = courseService.removeCourseSpotInternal(userId, courseId, spotId);
        if (dayNumber != null) {
            recalculateTravelTimesForDay(courseId, dayNumber);
        }
    }

    public void updateSpotOrder(Long userId, Long courseId, CourseSpotOrderUpdateRequest request) {
        Set<Integer> affectedDays = courseService.updateSpotOrderInternal(userId, courseId, request);
        for (Integer dayNumber : affectedDays) {
            recalculateTravelTimesForDay(courseId, dayNumber);
        }
    }

    private void recalculateTravelTimesForDay(Long courseId, Integer dayNumber) {
        List<CourseSpotResponse> daySpots = courseService.getDaySpots(courseId, dayNumber);

        if (daySpots.isEmpty() || daySpots.size() == 1) return;

        List<Long> spotIds = daySpots.stream().map(CourseSpotResponse::spotId).toList();
        Map<Long, Spot> spotMap = spotRepository.findByIdIn(spotIds).stream()
                .collect(Collectors.toMap(Spot::getId, s -> s));

        Map<Long, Integer> travelTimeUpdates = new HashMap<>();
        travelTimeUpdates.put(daySpots.get(0).id(), null);

        for (int i = 1; i < daySpots.size(); i++) {
            CourseSpotResponse currentSpot = daySpots.get(i);
            CourseSpotResponse prevSpot = daySpots.get(i - 1);
            
            Spot s1 = spotMap.get(prevSpot.spotId());
            Spot s2 = spotMap.get(currentSpot.spotId());

            if (s1 != null && s2 != null) {
                Integer travelTime = routeCacheService.getTravelTimeMinutes(
                        s1.getLatitude(), s1.getLongitude(),
                        s2.getLatitude(), s2.getLongitude()
                );
                travelTimeUpdates.put(currentSpot.id(), travelTime);
            } else {
                travelTimeUpdates.put(currentSpot.id(), 30); // fallback
            }
        }

        courseService.updateTravelTimes(courseId, travelTimeUpdates);
    }
}
