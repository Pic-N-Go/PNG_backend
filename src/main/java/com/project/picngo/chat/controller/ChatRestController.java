package com.project.picngo.chat.controller;

import com.project.picngo.chat.dto.ChatMessageResponse;
import com.project.picngo.chat.dto.ChatParticipantResponse;
import com.project.picngo.chat.service.ChatMessageService;
import com.project.picngo.chat.service.ChatParticipantService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/chats")
@RequiredArgsConstructor
public class ChatRestController implements ChatRestControllerApiSpec{
    private final ChatMessageService chatMessageService;
    private final ChatParticipantService chatParticipantService;

    @Override
    @GetMapping("/{spotId}/messages")
    public ResponseEntity<List<ChatMessageResponse>> getMessages(@PathVariable Long spotId) {
        return ResponseEntity.ok(chatMessageService.getMessages(spotId));
    }

    @Override
    @GetMapping("/{spotId}/preview")
    public ResponseEntity<List<ChatMessageResponse>> getPreviewMessages(@PathVariable Long spotId) {
        return ResponseEntity.ok(chatMessageService.getPreviewMessages(spotId));
    }

    @Override
    @GetMapping("/{spotId}/participants/count")
    public ResponseEntity<Long> getParticipantCount(@PathVariable Long spotId) {
        return ResponseEntity.ok(chatParticipantService.getParticipantCount(spotId));
    }

    @Override
    @GetMapping("/{spotId}/participants")
    public ResponseEntity<List<ChatParticipantResponse>> getParticipants(@PathVariable Long spotId) {
        return ResponseEntity.ok(chatParticipantService.getParticipants(spotId));
    }
}
