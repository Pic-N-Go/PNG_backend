package com.project.picngo.chat.event;


import com.project.picngo.chat.service.ChatRoomService;
import com.project.picngo.common.event.SpotCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ChatRoomEventListener {

    private final ChatRoomService chatRoomService;

    //TODO: Spot 생성 로직이 구현되었을 때 이벤트 발행하도록 연결

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleSpotCreated(SpotCreatedEvent event) {
        chatRoomService.createForSpot(event.spotId());
    }
}
