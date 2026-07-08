package com.project.picngo.chat.event;


import com.project.picngo.auth.service.CustomUserDetails;
import com.project.picngo.chat.service.ChatParticipantService;
import com.project.picngo.chat.service.ChatRoomService;
import com.project.picngo.common.event.SpotCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ChatRoomEventListener {

    private final ChatRoomService chatRoomService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ChatParticipantService chatParticipantService;

    //TODO: Spot 생성 로직이 구현되었을 때 이벤트 발행하도록 연결

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleSpotCreated(SpotCreatedEvent event) {
        chatRoomService.createForSpot(event.spotId());
    }


    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.wrap(event.getMessage());

        Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();

        if (sessionAttributes == null) {
            return;
        }

        Object spotIdValue = sessionAttributes.get("spotId");

        if (spotIdValue == null) {
            return;
        }

        Principal principal = event.getUser();
        if (!(principal instanceof Authentication authentication) || !(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            return;
        }

        Long spotId = (Long) spotIdValue;

        chatParticipantService.leave(spotId, userDetails.getId());

        long participantCount = chatParticipantService.getParticipantCount(spotId);
        messagingTemplate.convertAndSend("/topic/chats/" + spotId + "/participants/count", participantCount);
    }
}
