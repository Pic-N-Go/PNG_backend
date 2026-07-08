package com.project.picngo.chat.controller;

import com.project.picngo.auth.service.CustomUserDetails;
import com.project.picngo.chat.dto.ChatMessageResponse;
import com.project.picngo.chat.dto.ChatMessageSendRequest;
import com.project.picngo.chat.service.ChatMessageService;
import com.project.picngo.chat.service.ChatParticipantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class ChatSocketController {
    private final ChatMessageService chatMessageService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ChatParticipantService chatParticipantService;

    @MessageMapping("/chats/{spotId}/messages")
    public void sendMessage(
            @DestinationVariable Long spotId,
            @Valid ChatMessageSendRequest request,
            Principal principal
    ) {
        CustomUserDetails userDetails = getUserDetails(principal);

        ChatMessageResponse response = chatMessageService.sendMessage(
                spotId,
                userDetails.getId(),
                userDetails.getNickname(),
                request
        );

        messagingTemplate.convertAndSend("/topic/chats/" + spotId, response);
    }

    @MessageMapping("/chats/{spotId}/enter")
    public void enter(
            @DestinationVariable Long spotId,
            Principal principal,
            SimpMessageHeaderAccessor headerAccessor
    ) {
        CustomUserDetails userDetails = getUserDetails(principal);

        chatParticipantService.enter(spotId, userDetails.getId(), userDetails.getNickname());

        if (headerAccessor.getSessionAttributes() != null) {
            headerAccessor.getSessionAttributes().put("spotId", spotId);
        }

        long participantCount = chatParticipantService.getParticipantCount(spotId);

        messagingTemplate.convertAndSend("/topic/chats/" + spotId + "/participants/count", participantCount);
    }

    @MessageMapping("/chats/{spotId}/leave")
    public void leave(
            @DestinationVariable Long spotId,
            Principal principal,
            SimpMessageHeaderAccessor headerAccessor
    ) {
        CustomUserDetails userDetails = getUserDetails(principal);

        chatParticipantService.leave(spotId, userDetails.getId());

        if (headerAccessor.getSessionAttributes() != null) {
            headerAccessor.getSessionAttributes().remove("spotId");
        }

        long participantCount = chatParticipantService.getParticipantCount(spotId);

        messagingTemplate.convertAndSend("/topic/chats/" + spotId + "/participants/count", participantCount);
    }

    private CustomUserDetails getUserDetails(Principal principal) {
        if (!(principal instanceof Authentication authentication)
                || !(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            throw new AccessDeniedException("인증된 사용자만 채팅을 사용할 수 있습니다.");
        }
        return userDetails;
    }
}
