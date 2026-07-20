package com.project.picngo.course.service;

import com.project.picngo.course.dto.CourseSpotResponse;
import com.project.picngo.course.dto.CourseSpotSyncRequest;
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



    public void syncCourseSpots(Long userId, Long courseId, CourseSpotSyncRequest request) {
        courseService.syncCourseSpots(userId, courseId, request);
        recalculateTravelTimesForDay(courseId, request.dayNumber());
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
