package com.project.picngo.community.dto;

import com.project.picngo.community.domain.PostWeather;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;
import java.util.List;

public record PostUpdateRequest(
        @Size(max = 2000)
        @Pattern(
                regexp = "(?s).*\\S.*",
                message = "게시글 내용은 공백일 수 없습니다."
        )
        String content,

        @Positive
        Long spotId,

        LocalTime shootingTime,

        PostWeather weather,

        @Size(max = 100)
        String cameraModel,

        @Size(max = 150)
        String lensModel,

        @Size(max = 10)
        List<@NotBlank @Size(max = 30) String> tags,

        @Size(max = 5)
        List<@Positive Long> retainedImageIds
) {
}
