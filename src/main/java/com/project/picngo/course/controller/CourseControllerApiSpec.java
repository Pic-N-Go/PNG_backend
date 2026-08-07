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

    @Operation(summary = "코스 삭제", description = "자신의 코스를 삭제합니다.")
    ResponseEntity<Void> deleteCourse(@Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable Long id);

    @Operation(summary = "코스 스팟 일괄 동기화", description = "특정 일차(Day)의 코스 스팟 리스트를 일괄 동기화(추가/삭제/수정)합니다.")
    ResponseEntity<Void> syncCourseSpots(@Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable Long id, @RequestBody CourseSpotSyncRequest request);

    @Operation(summary = "코스 체크리스트 추가", description = "코스에 새로운 체크리스트 항목을 추가합니다.")
    ResponseEntity<CourseChecklistResponse> addCourseChecklist(@Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable Long id, @RequestBody CourseChecklistRequest request);

    @Operation(summary = "코스 체크리스트 완료 토글", description = "코스 체크리스트 항목의 완료 여부를 토글합니다.")
    ResponseEntity<CourseChecklistResponse> toggleCourseChecklist(@Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable Long id, @PathVariable Long checklistId);

    @Operation(summary = "코스 체크리스트 내용 수정", description = "코스 체크리스트 항목의 내용을 수정합니다.")
    ResponseEntity<CourseChecklistResponse> updateCourseChecklist(@Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable Long id, @PathVariable Long checklistId, @RequestBody CourseChecklistRequest request);

    @Operation(summary = "코스 체크리스트 삭제", description = "코스 체크리스트 항목을 삭제합니다.")
    ResponseEntity<Void> deleteCourseChecklist(@Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable Long id, @PathVariable Long checklistId);

    @Operation(summary = "코스 날씨 조회", description = "코스의 방문 일차별 대상 스팟의 기상청 날씨와 일출/일몰 정보를 반환합니다.")
    ResponseEntity<List<CourseWeatherResponse>> getCourseWeather(@Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable Long id);
}
