package com.project.picngo.common.exception.code;

import com.project.picngo.common.exception.BaseErrorCode;
import org.springframework.http.HttpStatus;

public enum ChatRoomErrorCode implements BaseErrorCode {

    CHAT_ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 스팟의 채팅방을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String message;

    ChatRoomErrorCode(HttpStatus status, String message) {
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
