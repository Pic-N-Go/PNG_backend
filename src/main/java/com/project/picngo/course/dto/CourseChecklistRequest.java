package com.project.picngo.course.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CourseChecklistRequest(
        @NotBlank(message = "내용을 입력해주세요.")
        @Size(max = 200, message = "내용은 200자 이내여야 합니다.")
        String content
) {}
