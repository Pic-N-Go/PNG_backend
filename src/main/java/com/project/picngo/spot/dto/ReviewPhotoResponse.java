package com.project.picngo.spot.dto;

// presigned URL은 요청마다 서명이 바뀌어 식별자로 쓸 수 없다. 삭제 대상 지정은 photoId로 한다.
public record ReviewPhotoResponse(
        Long photoId,
        String url
) {
}
