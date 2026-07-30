package com.project.picngo.common.exception.code;

import com.project.picngo.common.exception.BaseErrorCode;
import org.springframework.http.HttpStatus;

public enum CommunityErrorCode implements BaseErrorCode {
    POST_IMAGE_REQUIRED(HttpStatus.BAD_REQUEST, "게시글 이미지는 최소 1개 이상 필요합니다."),
    POST_IMAGE_TOO_MANY(HttpStatus.BAD_REQUEST, "게시글 이미지는 최대 5개까지 등록할 수 있습니다."),
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "게시글을 찾을 수 없습니다."),
    POST_FORBIDDEN(HttpStatus.FORBIDDEN, "게시글을 수정하거나 삭제할 권한이 없습니다."),
    POST_IMAGE_INVALID(HttpStatus.BAD_REQUEST, "게시글에 속하지 않은 이미지가 포함되어 있습니다."),
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "댓글을 찾을 수 없습니다."),
    COMMENT_FORBIDDEN(HttpStatus.FORBIDDEN, "댓글을 수정하거나 삭제할 권한이 없습니다."),
    // 팔로우 기능 연동 후 기능 구현
    FOLLOWING_FEED_NOT_AVAILABLE(HttpStatus.NOT_IMPLEMENTED, "팔로잉 기능이 아직 구현되지 않았습니다.");

    private final HttpStatus status;
    private final String message;

    CommunityErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    @Override
    public HttpStatus getStatus() {
        return status;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
