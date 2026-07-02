package com.project.picngo.course.controller;

import com.project.picngo.course.dto.*;
import com.project.picngo.course.service.CourseFacade;
import com.project.picngo.course.service.CourseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.project.picngo.auth.service.CustomUserDetails;
import java.util.List;

@RestController
@RequestMapping("/courses")
@RequiredArgsConstructor
public class CourseController implements CourseControllerApiSpec {

    private final CourseService courseService;
    private final CourseFacade courseFacade;

    @GetMapping
    public ResponseEntity<List<CourseResponse>> getCourses(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(courseService.getCourses(userDetails.getId()));
    }

    @PostMapping
    public ResponseEntity<CourseResponse> createCourse(@AuthenticationPrincipal CustomUserDetails userDetails, @RequestBody CourseCreateRequest request) {
        return ResponseEntity.ok(courseService.createCourse(userDetails.getId(), request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CourseDetailResponse> getCourseDetail(@PathVariable Long id) {
        return ResponseEntity.ok(courseService.getCourseDetail(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CourseResponse> updateCourse(@PathVariable Long id, @RequestBody CourseCreateRequest request) {
        return ResponseEntity.ok(courseService.updateCourse(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCourse(@PathVariable Long id) {
        courseService.deleteCourse(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/spots")
    public ResponseEntity<CourseSpotResponse> addCourseSpot(@PathVariable Long id, @RequestBody CourseSpotAddRequest request) {
        return ResponseEntity.ok(courseFacade.addCourseSpot(id, request));
    }

    @DeleteMapping("/{id}/spots/{spotId}")
    public ResponseEntity<Void> removeCourseSpot(@PathVariable Long id, @PathVariable Long spotId) {
        courseFacade.removeCourseSpot(id, spotId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/spots/order")
    public ResponseEntity<Void> updateSpotOrder(@PathVariable Long id, @RequestBody CourseSpotOrderUpdateRequest request) {
        courseFacade.updateSpotOrder(id, request);
        return ResponseEntity.ok().build();
    }
}
