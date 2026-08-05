package com.project.picngo.chat.domain;

import com.project.picngo.common.domain.BaseTimeEntity;
import com.project.picngo.spot.domain.Spot;
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

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "spot_id", nullable = false, unique = true)
    private Spot spot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChatRoomStatus status;

    private ChatRoom(Spot spot) {
        this.spot = spot;
        this.status = ChatRoomStatus.ACTIVE;
    }

    public static ChatRoom create(Spot spot) {
        return new ChatRoom(spot);
    }

    public boolean isActive() {
        return this.status == ChatRoomStatus.ACTIVE;
    }
}
