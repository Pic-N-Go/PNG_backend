package com.project.picngo.bookmark.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCollectionRequest(
        @NotBlank(message = "컬렉션 이름은 필수입니다.")
        @Size(max = 20, message = "컬렉션 이름은 최대 20자입니다.")
        String name,

        @NotBlank(message = "색상 키는 필수입니다.")
        String color,

        @NotBlank(message = "아이콘 키는 필수입니다.")
        String icon
) {}
