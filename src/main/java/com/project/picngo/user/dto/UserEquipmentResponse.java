package com.project.picngo.user.dto;

import com.project.picngo.user.domain.EquipmentType;
import com.project.picngo.user.domain.UserEquipment;

public record UserEquipmentResponse(
        Long id,
        EquipmentType equipmentType,
        String equipmentName
) {

    public static UserEquipmentResponse from(
            UserEquipment equipment
    ) {
        return new UserEquipmentResponse(
                equipment.getId(),
                equipment.getEquipmentType(),
                equipment.getEquipmentName()
        );
    }
}
