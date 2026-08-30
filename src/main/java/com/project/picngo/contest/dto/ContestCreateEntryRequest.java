package com.project.picngo.contest.dto;

import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record ContestCreateEntryRequest(

        @Size(max = 80, message = "설명은 최대 80자까지 입력할 수 있습니다.")
        String caption,

        Long spotId,

        @Size(max = 100, message = "장소명은 최대 100자까지 입력할 수 있습니다.")
        String spotName,

        /**
         * 클라이언트가 원본 사진에서 읽은 촬영 시각(EXIF DateTimeOriginal).
         *
         * 앱의 이미지 피커가 업로드 전에 사진을 재인코딩해서 메타데이터를 떨어뜨린다.
         * 서버가 받은 바이트에서 다시 뽑으려 해도 이미 없는 경우가 대부분이라,
         * 원본을 손에 쥔 클라이언트가 읽어서 보내는 쪽이 정확하다.
         * 없으면 서버가 업로드된 파일에서 추출을 시도한다.
         */
        LocalDateTime shotAt
) {
}
