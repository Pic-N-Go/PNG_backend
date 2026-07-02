package com.project.picngo.chat.repository;

import com.project.picngo.chat.domain.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findTop50ByChatRoomIdOrderByCreatedAtDesc(Long chatRoomId);

    List<ChatMessage> findTop3ByChatRoomIdOrderByCreatedAtDesc(Long chatRoomId);
}
