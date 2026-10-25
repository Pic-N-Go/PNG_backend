package com.project.picngo.user.dto;

import com.project.picngo.user.domain.User;
import com.project.picngo.user.domain.UserBlock;

import java.time.LocalDateTime;

/** 차단 목록 한 줄. 해제 버튼을 달려면 id가, 누구인지 알려면 닉네임·사진이 필요하다. */
public record BlockedUserResponse(Long id,
                                  String nickname,
                                  String profileImageUrl,
                                  LocalDateTime blockedAt) {

    public static BlockedUserResponse from(UserBlock block, String profileImageUrl) {
        User blocked = block.getBlocked();
        return new BlockedUserResponse(
                blocked.getId(),
                blocked.getNickname(),
                profileImageUrl,
                block.getCreatedAt()
        );
    }
}
