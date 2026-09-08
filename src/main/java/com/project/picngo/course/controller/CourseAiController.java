package com.project.picngo.course.controller;

import com.project.picngo.auth.service.CustomUserDetails;
import com.project.picngo.course.agent.AiCoursePlanService;
import com.project.picngo.course.agent.dto.AiCoursePlanRequest;
import com.project.picngo.course.agent.dto.AiCoursePlanResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "AI 코스 플래너", description = "멀티 에이전트 기반 맞춤형 출사 코스 기획 API")
@RestController
@RequestMapping("/api/v1/courses/ai-plan")
@RequiredArgsConstructor
public class CourseAiController {

    private final AiCoursePlanService aiCoursePlanService;

    @Operation(summary = "AI 출사 코스 기획 비동기 요청 (202 Accepted)", description = "자연어 질의를 바탕으로 멀티 에이전트가 백그라운드에서 코스를 기획합니다.")
    @PostMapping
    public ResponseEntity<AiCoursePlanResponse> requestAiCoursePlan(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody AiCoursePlanRequest request
    ) {
        String taskId = aiCoursePlanService.initiatePlan(userDetails.getId(), request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(AiCoursePlanResponse.accepted(taskId));
    }

    @Operation(summary = "AI 출사 코스 기획 작업 상태 조회", description = "발급받은 taskId로 코스 기획의 진행 상태 및 완성된 코스 ID를 확인합니다.")
    @GetMapping("/{taskId}")
    public ResponseEntity<AiCoursePlanResponse> getTaskStatus(
            @PathVariable String taskId
    ) {
        return ResponseEntity.ok(aiCoursePlanService.getTaskStatus(taskId));
    }
}
