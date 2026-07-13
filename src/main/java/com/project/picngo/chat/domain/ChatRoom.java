package com.project.picngo.chat.domain;

import com.project.picngo.common.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoom extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    //TODO: Spot 도메인 확정 후 spotId를 Spot 연관관계로 전환
    @Column(nullable = false, unique = true)
    private Long spotId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChatRoomStatus status;

    private ChatRoom(Long spotId) {
        this.spotId = spotId;
        this.status = ChatRoomStatus.ACTIVE;
    }

    public static ChatRoom create(Long spotId) {
        return new ChatRoom(spotId);
    }

    public boolean isActive() {
        return this.status == ChatRoomStatus.ACTIVE;
    }
}
