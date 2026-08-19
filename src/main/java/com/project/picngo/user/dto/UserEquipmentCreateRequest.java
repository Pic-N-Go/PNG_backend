package com.project.picngo.user.dto;

import com.project.picngo.user.domain.EquipmentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UserEquipmentCreateRequest(

        @NotNull(message = "장비 종류는 필수입니다.")
        EquipmentType equipmentType,

        @NotBlank(message = "장비 이름은 필수입니다.")
        @Size(max = 100, message = "장비 이름은 최대 100자 입니다.")
        String equipmentName
) {
}
