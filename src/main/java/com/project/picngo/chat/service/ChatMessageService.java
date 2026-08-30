package com.project.picngo.chat.service;

import com.project.picngo.chat.domain.ChatMessage;
import com.project.picngo.chat.domain.ChatRoom;
import com.project.picngo.chat.dto.ChatMessageResponse;
import com.project.picngo.chat.dto.ChatMessageSendRequest;
import com.project.picngo.chat.repository.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatMessageService {
    private final ChatRoomService chatRoomService;
    private final ChatMessageRepository chatMessageRepository;

    private static final int MAX_MESSAGE_SIZE = 50;

    @Transactional
    public ChatMessageResponse sendMessage(
            Long spotId,
            Long senderId,
            String senderNickname,
            ChatMessageSendRequest request
    ) {
        ChatRoom chatRoom = chatRoomService.getBySpotId(spotId);

        ChatMessage message = ChatMessage.text(
                chatRoom.getId(),
                senderId,
                senderNickname,
                request.content()
        );

        return ChatMessageResponse.from(chatMessageRepository.save(message));
    }

    public List<ChatMessageResponse> getMessages(Long spotId, Long beforeId, int size) {
        ChatRoom chatRoom = chatRoomService.getBySpotId(spotId);

        int normalizedSize = Math.min(Math.max(size, 1), MAX_MESSAGE_SIZE);
        Pageable pageable = PageRequest.of(0, normalizedSize);

        List<ChatMessage> messages = beforeId == null ?
                chatMessageRepository.findByChatRoomIdOrderByIdDesc(chatRoom.getId(), pageable)
                : chatMessageRepository.findByChatRoomIdAndIdLessThanOrderByIdDesc(chatRoom.getId(), beforeId, pageable);

        Collections.reverse(messages);

        return messages.stream()
                .map(ChatMessageResponse::from)
                .toList();
    }

    public List<ChatMessageResponse> getPreviewMessages(Long spotId) {
        ChatRoom chatRoom = chatRoomService.getBySpotId(spotId);

        List<ChatMessage> messages = chatMessageRepository
                .findTop3ByChatRoomIdOrderByCreatedAtDesc(chatRoom.getId());

        Collections.reverse(messages);

        return messages.stream()
                .map(ChatMessageResponse::from)
                .toList();
    }
}
