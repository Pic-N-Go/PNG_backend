package com.project.picngo.user.service;

import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.UserEquipmentErrorCode;
import com.project.picngo.user.domain.UserEquipment;
import com.project.picngo.user.dto.UserEquipmentCreateRequest;
import com.project.picngo.user.dto.UserEquipmentResponse;
import com.project.picngo.user.dto.UserEquipmentUpdateRequest;
import com.project.picngo.user.repository.UserEquipmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserEquipmentService {
    private static final int MAX_EQUIPMENT_COUNT = 20;

    private final UserEquipmentRepository userEquipmentRepository;

    public List<UserEquipmentResponse> getMyEquipments(Long userId){
        return userEquipmentRepository.findAllByUserIdOrderByCreatedAtAsc(userId)
                .stream()
                .map(UserEquipmentResponse::from)
                .toList();
    }

    @Transactional
    public UserEquipmentResponse createUserEquipment(Long userId, UserEquipmentCreateRequest request){
        String equipmentName = request.equipmentName().trim();

        validateDuplicate(userId, request, equipmentName);

        validateEquipmentCount(userId);

        UserEquipment equipment = UserEquipment.create(userId, request.equipmentType(), equipmentName);

        try {
            UserEquipment savedEquipment = userEquipmentRepository.saveAndFlush(equipment);
            return UserEquipmentResponse.from(savedEquipment);
        } catch (DataIntegrityViolationException exception){
            throw new CustomException(UserEquipmentErrorCode.USER_EQUIPMENT_ALREADY_EXISTS);
        }
    }

    @Transactional
    public UserEquipmentResponse updateUserEquipment(Long userId, Long equipmentId, UserEquipmentUpdateRequest request){
        UserEquipment equipment = getOwnedEquipment(userId, equipmentId);

        String equipmentName = request.equipmentName().trim();

        boolean duplicate = userEquipmentRepository.existsByUserIdAndEquipmentTypeAndEquipmentNameAndIdNot(
                userId,
                request.equipmentType(),
                equipmentName,
                equipmentId
        );

        if(duplicate){
            throw new CustomException(UserEquipmentErrorCode.USER_EQUIPMENT_ALREADY_EXISTS);
        }

        equipment.update(request.equipmentType(), equipmentName);

        try {
            userEquipmentRepository.flush();
            return UserEquipmentResponse.from(equipment);
        } catch (DataIntegrityViolationException exception){
            throw new CustomException(UserEquipmentErrorCode.USER_EQUIPMENT_ALREADY_EXISTS);
        }
    }

    @Transactional
    public void deleteUserEquipment(Long userId, Long equipmentId) {
        UserEquipment equipment = getOwnedEquipment(userId, equipmentId);

        userEquipmentRepository.delete(equipment);
    }

    private void validateDuplicate(Long userId, UserEquipmentCreateRequest request, String equipmentName){
        boolean duplicate = userEquipmentRepository.existsByUserIdAndEquipmentTypeAndEquipmentName(
                userId,
                request.equipmentType(),
                equipmentName
        );

        if(duplicate){
            throw new CustomException(UserEquipmentErrorCode.USER_EQUIPMENT_ALREADY_EXISTS);
        }
    }

    private void validateEquipmentCount(Long userId) {
        long equipmentCount = userEquipmentRepository.countByUserId(userId);

        if (equipmentCount >= MAX_EQUIPMENT_COUNT) {
            throw new CustomException(UserEquipmentErrorCode.USER_EQUIPMENT_LIMIT_EXCEEDED);
        }
    }
    private UserEquipment getOwnedEquipment(Long userId, Long equipmentId) {
        return userEquipmentRepository.findByIdAndUserId(equipmentId, userId)
                .orElseThrow(() -> new CustomException(UserEquipmentErrorCode.USER_EQUIPMENT_NOT_FOUND));
    }
}
