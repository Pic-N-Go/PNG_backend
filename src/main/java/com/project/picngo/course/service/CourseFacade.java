package com.project.picngo.course.service;

import com.project.picngo.course.dto.CourseSpotAddRequest;
import com.project.picngo.course.dto.CourseSpotOrderUpdateRequest;
import com.project.picngo.course.dto.CourseSpotResponse;
import com.project.picngo.external.DirectionsClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CourseFacade {

    private final CourseService courseService;
    private final DirectionsClient directionsClient;

    public CourseSpotResponse addCourseSpot(Long courseId, CourseSpotAddRequest request) {
        CourseSpotResponse response = courseService.addCourseSpotInternal(courseId, request);
        recalculateTravelTimesForDay(courseId, request.dayNumber());
        return response;
    }

    public void removeCourseSpot(Long courseId, Long spotId) {
        Integer dayNumber = courseService.removeCourseSpotInternal(courseId, spotId);
        if (dayNumber != null) {
            recalculateTravelTimesForDay(courseId, dayNumber);
        }
    }

    public void updateSpotOrder(Long courseId, CourseSpotOrderUpdateRequest request) {
        Set<Integer> affectedDays = courseService.updateSpotOrderInternal(courseId, request);
        for (Integer dayNumber : affectedDays) {
            recalculateTravelTimesForDay(courseId, dayNumber);
        }
    }

    private void recalculateTravelTimesForDay(Long courseId, Integer dayNumber) {
        List<CourseSpotResponse> daySpots = courseService.getDaySpots(courseId, dayNumber);

        if (daySpots.isEmpty()) return;

        Map<Long, Integer> travelTimeUpdates = new HashMap<>();
        travelTimeUpdates.put(daySpots.get(0).id(), null);

        for (int i = 1; i < daySpots.size(); i++) {
            CourseSpotResponse currentSpot = daySpots.get(i);
            
            Double startLat = 37.5546;
            Double startLng = 126.9725;
            Double goalLat = 37.4979;
            Double goalLng = 127.0276;

            try {
                Integer travelTime = directionsClient.getTravelTimeMinutes(startLat, startLng, goalLat, goalLng);
                travelTimeUpdates.put(currentSpot.id(), travelTime);
            } catch (Exception e) {
                travelTimeUpdates.put(currentSpot.id(), 30);
            }
        }

        courseService.updateTravelTimes(courseId, travelTimeUpdates);
    }
}
