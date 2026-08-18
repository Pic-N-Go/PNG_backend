package com.project.picngo.user.repository;

import com.project.picngo.user.domain.EquipmentType;
import com.project.picngo.user.domain.UserEquipment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserEquipmentRepository extends JpaRepository<UserEquipment, Long> {

    List<UserEquipment> findAllByUserIdOrderByCreatedAtAsc(Long userId);

    Optional<UserEquipment> findByIdAndUserId(Long equipmentId, Long userId);

    boolean existsByUserIdAndEquipmentTypeAndEquipmentName(Long userId, EquipmentType equipmentType, String equipmentName);

    boolean existsByUserIdAndEquipmentTypeAndEquipmentNameAndIdNot(
            Long userId,
            EquipmentType equipmentType,
            String equipmentName,
            Long equipmentId
    );

    long countByUserId(Long userId);
}
