package com.project.picngo.chat.repository;

import com.project.picngo.chat.domain.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {
    Optional<ChatRoom> findBySpot_Id(Long spotId);

    @Query("""
        select s.id
        from Spot s
        where not exists(
                select cr.id
                from ChatRoom cr
                where cr.spot.id = s.id
                )
        order by s.id
                        """)
    List<Long> findSpotIdWithoutChatRoom();
}
