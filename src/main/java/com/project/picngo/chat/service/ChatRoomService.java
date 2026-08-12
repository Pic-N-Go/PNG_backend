package com.project.picngo.chat.service;


import com.project.picngo.chat.domain.ChatRoom;
import com.project.picngo.chat.repository.ChatRoomRepository;
import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.ChatRoomErrorCode;
import com.project.picngo.common.exception.code.SpotErrorCode;
import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.repository.SpotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatRoomService {

    private final ChatRoomRepository chatRoomRepository;
    private final SpotRepository spotRepository;

    @Transactional
    public ChatRoom createForSpot(Long spotId) {
        return chatRoomRepository.findBySpot_Id(spotId)
                .orElseGet(() -> {
                    Spot spot = spotRepository.findById(spotId)
                            .orElseThrow(() ->
                                    new CustomException(SpotErrorCode.SPOT_NOT_FOUND));
                    return chatRoomRepository.save(ChatRoom.create(spot));
                });
    }

    public ChatRoom getBySpotId(Long spotId) {
        return chatRoomRepository.findBySpot_Id(spotId)
                .orElseThrow(() -> new CustomException(ChatRoomErrorCode.CHAT_ROOM_NOT_FOUND));
    }
}
