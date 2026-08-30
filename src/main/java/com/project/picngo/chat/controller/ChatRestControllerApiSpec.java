package com.project.picngo.chat.controller;

import com.project.picngo.chat.dto.ChatMessageResponse;
import com.project.picngo.chat.dto.ChatParticipantResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Tag(name = "채팅 (Chat)", description = "스팟 채팅방 메시지 조회, 미리보기 및 참여자 목록 관리 API")
public interface ChatRestControllerApiSpec {

    @Operation(summary = "채팅 메시지 조회", description = "스팟 채팅방의 최근 메시지 목록을 조회합니다.")
    ResponseEntity<List<ChatMessageResponse>> getMessages(
            @PathVariable Long spotId,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(defaultValue = "20") int size
    );

    @Operation(summary = "채팅 미리보기 조회", description = "스팟 채팅방의 최근 메시지 3개를 조회합니다.")
    ResponseEntity<List<ChatMessageResponse>> getPreviewMessages(@PathVariable Long spotId);

    @Operation(summary = "채팅 참여 인원 조회", description = "스팟 채팅방에 현재 참여 중인 사용자 수를 조회합니다.")
    ResponseEntity<Long> getParticipantCount(@PathVariable Long spotId);

    @Operation(summary = "채팅 참여자 목록 조회", description = "스팟 채팅방에 현재 참여 중인 사용자 목록을 조회합니다.")
    ResponseEntity<List<ChatParticipantResponse>> getParticipants(@PathVariable Long spotId);
}
