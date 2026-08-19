package com.project.picngo.user.domain;

import com.project.picngo.common.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "user_equipment",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_equipment_type_name",
                columnNames = {
                        "user_id",
                        "equipment_type",
                        "equipment_name"
                }
        ),
        indexes = @Index(
                name = "idx_user_equipment_user_id",
                columnList = "user_id"
        )
)
public class UserEquipment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "equipment_type", nullable = false, length = 20)
    private EquipmentType equipmentType;

    @Column(name = "equipment_name", nullable = false, length = 100)
    private String equipmentName;

    private UserEquipment(Long userId, EquipmentType equipmentType, String equipmentName) {
        this.userId = userId;
        this.equipmentType = equipmentType;
        this.equipmentName = equipmentName;
    }

    public static UserEquipment create(Long userId, EquipmentType equipmentType, String equipmentName) {
        return new UserEquipment(userId, equipmentType, equipmentName);
    }

    public void update(EquipmentType equipmentType, String equipmentName) {
        this.equipmentType = equipmentType;
        this.equipmentName = equipmentName;
    }
}
