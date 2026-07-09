package com.project.picngo.chat.domain;

import com.project.picngo.common.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long chatRoomId;

    @Column(nullable = false)
    private Long senderId;

    @Column(nullable = false, length = 100)
    private String senderNickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChatMessageType type;

    @Column(nullable = false, length = 1000)
    private String content;

    private ChatMessage(
            Long chatRoomId,
            Long senderId,
            String senderNickname,
            ChatMessageType type,
            String content
    ) {
        this.chatRoomId = chatRoomId;
        this.senderId = senderId;
        this.senderNickname = senderNickname;
        this.type = type;
        this.content = content;
    }

    //TODO: 이미지/시스템 메시지 기능 구현 시 ChatMessageType 확장
    public static ChatMessage text(Long chatRoomId, Long senderId, String senderNickname, String content) {
        return new ChatMessage(chatRoomId, senderId, senderNickname, ChatMessageType.TEXT, content);
    }
}
