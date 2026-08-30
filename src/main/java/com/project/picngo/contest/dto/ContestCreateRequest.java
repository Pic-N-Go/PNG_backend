package com.project.picngo.contest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 콘테스트 개설 요청. 사람이 정하는 건 테마뿐이고
 * 출품·투표 기간과 발표 시각은 Contest.create()의 주기 규칙에서 파생된다.
 */
public record ContestCreateRequest(

        @NotBlank(message = "테마는 필수입니다.")
        @Size(max = 100, message = "테마는 최대 100자까지 입력할 수 있습니다.")
        String title,

        @Size(max = 500, message = "설명은 최대 500자까지 입력할 수 있습니다.")
        String description,

        @Size(max = 500, message = "테마 이미지 URL은 최대 500자까지 입력할 수 있습니다.")
        String themeImageUrl,

        /**
         * 비우면 직전 회차의 결과 발표 시각에 이어 붙는다(회차가 없으면 지금).
         * 집계 중 구간에 다음 회차가 겹치면 /contests/current가 새 회차를 골라
         * 발표 대기 중인 직전 회차가 어디에서도 안 잡히므로, 기본값을 쓰는 쪽이 안전하다.
         */
        LocalDateTime submitStartAt,

        @Positive(message = "1인 최대 출품 수는 1 이상이어야 합니다.")
        Integer maxEntriesPerUser,

        @Positive(message = "최대 투표 수는 1 이상이어야 합니다.")
        Integer voteLimit
) {
}
