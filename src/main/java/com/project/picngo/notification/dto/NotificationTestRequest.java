package com.project.picngo.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "테스트용 푸시 알림 발송 요청 DTO")
public record NotificationTestRequest(
        @Schema(description = "알림 제목", example = "픽앤고 테스트 알림 🔔")
        String title,
        
        @Schema(description = "알림 본문 내용", example = "프론트엔드 푸시 알림 수신 성공 테스트 메시지입니다!")
        String content,
        
        @Schema(description = "딥링크 이동 경로", example = "/spot-alerts/1")
        String deepLink
) {}
