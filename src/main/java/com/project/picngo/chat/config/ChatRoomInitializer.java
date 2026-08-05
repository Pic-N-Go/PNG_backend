package com.project.picngo.chat.config;

import com.project.picngo.chat.repository.ChatRoomRepository;
import com.project.picngo.chat.service.ChatRoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatRoomInitializer implements ApplicationRunner {

    //TODO 다중 인스턴스 운영 시 DB 일괄 INSERT, 분산 락 등 적용 필요
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomService chatRoomService;

    @Override
    public void run(ApplicationArguments args) {
        List<Long> missingSpotIds = chatRoomRepository.findSpotIdWithoutChatRoom();

        for (Long spotId : missingSpotIds) {
            chatRoomService.createForSpot(spotId);
        }

        if(missingSpotIds.isEmpty()) {
            log.info("채팅방 정합성 확인 완료");
        } else {
            log.info("채팅방 초기화 완료: {}개 생성", missingSpotIds.size());
        }
    }
}
