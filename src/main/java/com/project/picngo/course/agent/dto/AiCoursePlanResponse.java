package com.project.picngo.course.agent.dto;

public record AiCoursePlanResponse(
        String taskId,
        String status, // "PROCESSING", "COMPLETED", "FAILED"
        Long courseId,
        String title,
        String message
) {
    public static AiCoursePlanResponse accepted(String taskId) {
        return new AiCoursePlanResponse(
                taskId,
                "PROCESSING",
                null,
                null,
                "AI 에이전트 오케스트레이터가 맞춤형 출사 코스를 기획 중입니다. 완료 시 푸시 알림으로 전송됩니다."
        );
    }

    public static AiCoursePlanResponse completed(String taskId, Long courseId, String title) {
        return new AiCoursePlanResponse(
                taskId,
                "COMPLETED",
                courseId,
                title,
                "AI 맞춤형 출사 코스 기획이 성공적으로 완료되었습니다."
        );
    }

    public static AiCoursePlanResponse failed(String taskId, String message) {
        return new AiCoursePlanResponse(
                taskId,
                "FAILED",
                null,
                null,
                message
        );
    }
}
