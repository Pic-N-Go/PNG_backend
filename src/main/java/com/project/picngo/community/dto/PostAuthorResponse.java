package com.project.picngo.community.dto;

import com.project.picngo.user.domain.User;

public record PostAuthorResponse(
        Long id,
        String nickname,
        String profileImageUrl,
        /** 탈퇴 계정이면 true. 클라이언트가 프로필 이동을 막거나 다르게 표시할 수 있다. */
        boolean withdrawn
) {
    /**
     * 탈퇴 계정은 이름과 사진을 즉시 가린다 — 파기(30일)까지 기다리면 그 기간 동안
     * 탈퇴한 사람의 닉네임과 프로필 사진이 그대로 노출된다.
     *
     * 게시글·댓글 자체는 지우지 않는다. 남이 쓴 글에 달린 대화가 끊기기 때문이다.
     * DB는 건드리지 않으므로 복구하면 원래 이름이 그대로 돌아온다.
     */
    public static PostAuthorResponse from(User user, String profileImageUrl) {
        boolean withdrawn = user.isWithdrawn();
        return new PostAuthorResponse(
                user.getId(),
                withdrawn ? User.WITHDRAWN_DISPLAY_NAME : user.getNickname(),
                withdrawn ? null : profileImageUrl,
                withdrawn
        );
    }
}
