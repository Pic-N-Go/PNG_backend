package com.project.picngo.course.controller;

import com.project.picngo.course.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.project.picngo.auth.service.CustomUserDetails;
import io.swagger.v3.oas.annotations.Parameter;
import java.util.List;

@Tag(name = "코스 (Course)", description = "코스 생성, 조회 및 코스 내 방문 장소 관리 API")
public interface CourseControllerApiSpec {

    @Operation(summary = "전체 코스 목록 조회", description = "사용자가 생성하거나 접근 가능한 코스 목록을 반환합니다.")
    ResponseEntity<List<CourseResponse>> getCourses(@Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails);

    @Operation(summary = "새 코스 생성", description = "새로운 사진 코스를 생성합니다.")
    ResponseEntity<CourseResponse> createCourse(@Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails, @RequestBody CourseCreateRequest request);

    @Operation(summary = "코스 상세 조회", description = "특정 코스의 상세 정보와 포함된 명소 목록을 조회합니다.")
    @Parameter(name = "id", description = "코스 ID")
    ResponseEntity<CourseDetailResponse> getCourseDetail(@Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable Long id);

    @Operation(summary = "코스 정보 수정", description = "코스의 이름이나 설명을 수정합니다.")
    ResponseEntity<CourseResponse> updateCourse(@Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable Long id, @RequestBody CourseCreateRequest request);

    @Operation(summary = "코스 삭제", description = "특정 코스를 삭제합니다.")
    ResponseEntity<Void> deleteCourse(@Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable Long id);

    @Operation(summary = "코스에 명소 추가", description = "코스에 새로운 명소를 추가하고, 길찾기 소요시간을 갱신합니다.")
    ResponseEntity<CourseSpotResponse> addCourseSpot(@Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable Long id, @RequestBody CourseSpotAddRequest request);

    @Operation(summary = "코스에서 명소 제거", description = "코스에 포함된 명소를 제거하고 길찾기 소요시간을 재계산합니다.")
    ResponseEntity<Void> removeCourseSpot(@Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable Long id, @PathVariable Long spotId);

    @Operation(summary = "명소 순서 변경", description = "코스 내 명소들의 방문 순서를 변경하고 길찾기 데이터를 재계산합니다.")
    ResponseEntity<Void> updateSpotOrder(@Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable Long id, @RequestBody CourseSpotOrderUpdateRequest request);
}
