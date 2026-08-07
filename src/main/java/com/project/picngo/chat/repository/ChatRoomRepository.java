package com.project.picngo.chat.repository;

import com.project.picngo.chat.domain.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {
    Optional<ChatRoom> findBySpotId(Long spotId);

    boolean existsBySpotId(Long spotId);
}
