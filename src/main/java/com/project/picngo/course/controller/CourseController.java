package com.project.picngo.course.controller;

import com.project.picngo.course.dto.*;
import com.project.picngo.course.service.CourseFacade;
import com.project.picngo.course.service.CourseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/courses")
@RequiredArgsConstructor
public class CourseController implements CourseControllerApiSpec {

    private final CourseService courseService;
    private final CourseFacade courseFacade;

    // TODO: 추후 Spring Security 연동 시 인증된 사용자 ID를 자동으로 가져오도록 변경
    private static final Long TEMP_USER_ID = 1L;

    @GetMapping
    public ResponseEntity<List<CourseResponse>> getCourses() {
        return ResponseEntity.ok(courseService.getCourses(TEMP_USER_ID));
    }

    @PostMapping
    public ResponseEntity<CourseResponse> createCourse(@RequestBody CourseCreateRequest request) {
        return ResponseEntity.ok(courseService.createCourse(TEMP_USER_ID, request));
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
