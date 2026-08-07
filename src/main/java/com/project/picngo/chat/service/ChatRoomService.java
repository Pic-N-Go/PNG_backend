package com.project.picngo.chat.service;


import com.project.picngo.chat.domain.ChatRoom;
import com.project.picngo.chat.repository.ChatRoomRepository;
import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.ChatRoomErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatRoomService {

    private final ChatRoomRepository chatRoomRepository;

    @Transactional
    public ChatRoom createForSpot(Long spotId) {
        return chatRoomRepository.findBySpotId(spotId)
                .orElseGet(() -> chatRoomRepository.save(ChatRoom.create(spotId)));
    }

    public ChatRoom getBySpotId(Long spotId) {
        return chatRoomRepository.findBySpotId(spotId)
                .orElseThrow(() -> new CustomException(ChatRoomErrorCode.CHAT_ROOM_NOT_FOUND));
    }
}
