package com.project.picngo.course.controller;

import com.project.picngo.course.dto.*;
import com.project.picngo.course.service.CourseFacade;
import com.project.picngo.course.service.CourseService;
import com.project.picngo.course.service.CourseWeatherService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.project.picngo.auth.service.CustomUserDetails;
import java.util.List;

@RestController
@RequestMapping("/courses")
@RequiredArgsConstructor
public class CourseController implements CourseControllerApiSpec {

    private final CourseService courseService;
    private final CourseFacade courseFacade;
    private final CourseWeatherService courseWeatherService;

    @GetMapping
    public ResponseEntity<List<CourseResponse>> getCourses(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(courseService.getCourses(userDetails.getId()));
    }

    @PostMapping
    public ResponseEntity<CourseResponse> createCourse(@AuthenticationPrincipal CustomUserDetails userDetails, @RequestBody CourseCreateRequest request) {
        return ResponseEntity.ok(courseService.createCourse(userDetails.getId(), request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CourseDetailResponse> getCourseDetail(@AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable Long id) {
        return ResponseEntity.ok(courseService.getCourseDetail(userDetails.getId(), id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CourseResponse> updateCourse(@AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable Long id, @RequestBody CourseCreateRequest request) {
        return ResponseEntity.ok(courseService.updateCourse(userDetails.getId(), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCourse(@AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable Long id) {
        courseService.deleteCourse(userDetails.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/spots/sync")
    public ResponseEntity<Void> syncCourseSpots(@AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable Long id, @Valid @RequestBody CourseSpotSyncRequest request) {
        courseFacade.syncCourseSpots(userDetails.getId(), id, request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/checklists")
    public ResponseEntity<CourseChecklistResponse> addCourseChecklist(@AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable Long id, @Valid @RequestBody CourseChecklistRequest request) {
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(courseService.addCourseChecklist(userDetails.getId(), id, request));
    }

    @PutMapping("/{id}/checklists/{checklistId}")
    public ResponseEntity<CourseChecklistResponse> toggleCourseChecklist(@AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable Long id, @PathVariable Long checklistId) {
        return ResponseEntity.ok(courseService.toggleCourseChecklist(userDetails.getId(), id, checklistId));
    }

    @PatchMapping("/{id}/checklists/{checklistId}")
    public ResponseEntity<CourseChecklistResponse> updateCourseChecklist(@AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable Long id, @PathVariable Long checklistId, @Valid @RequestBody CourseChecklistRequest request) {
        return ResponseEntity.ok(courseService.updateCourseChecklist(userDetails.getId(), id, checklistId, request));
    }

    @DeleteMapping("/{id}/checklists/{checklistId}")
    public ResponseEntity<Void> deleteCourseChecklist(@AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable Long id, @PathVariable Long checklistId) {
        courseService.deleteCourseChecklist(userDetails.getId(), id, checklistId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/weather")
    public ResponseEntity<List<CourseWeatherResponse>> getCourseWeather(@AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable Long id) {
        return ResponseEntity.ok(courseWeatherService.getCourseWeather(userDetails.getId(), id));
    }
}
