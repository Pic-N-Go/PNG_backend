package com.project.picngo.spot.service;

import com.project.picngo.common.event.SpotCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 신규/갱신 스팟이 커밋된 뒤 임베딩을 계산한다.
 *
 * <p>AFTER_COMMIT + REQUIRES_NEW인 이유는 {@link com.project.picngo.chat.event.ChatRoomEventListener}와
 * 같다: 스팟 저장 트랜잭션이 끝나기 전에 외부 API(OpenAI)를 부르면 그 응답을 기다리는 동안
 * 원래 트랜잭션이 묶여 있게 된다. 여기서는 실패해도 스팟 저장 자체에는 영향이 없어야 하므로
 * 별도 트랜잭션으로 분리한다.
 */
@Component
@RequiredArgsConstructor
public class SpotEmbeddingEventListener {

    private final SpotEmbeddingService spotEmbeddingService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleSpotCreated(SpotCreatedEvent event) {
        spotEmbeddingService.embedSpot(event.spotId());
    }
}
