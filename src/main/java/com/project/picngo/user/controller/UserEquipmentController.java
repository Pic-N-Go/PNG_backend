package com.project.picngo.user.controller;

import com.project.picngo.auth.service.CustomUserDetails;
import com.project.picngo.user.dto.UserEquipmentCreateRequest;
import com.project.picngo.user.dto.UserEquipmentResponse;
import com.project.picngo.user.dto.UserEquipmentUpdateRequest;
import com.project.picngo.user.service.UserEquipmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/users/me/equipments")
public class UserEquipmentController
        implements UserEquipmentControllerApiSpec {

    private final UserEquipmentService userEquipmentService;

    @Override
    @GetMapping
    public ResponseEntity<List<UserEquipmentResponse>> getMyEquipments(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(userEquipmentService.getMyEquipments(userDetails.getId()));
    }

    @Override
    @PostMapping
    public ResponseEntity<UserEquipmentResponse> createUserEquipment(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody UserEquipmentCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userEquipmentService.createUserEquipment(userDetails.getId(), request));
    }

    @Override
    @PutMapping("/{equipmentId}")
    public ResponseEntity<UserEquipmentResponse> updateUserEquipment(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long equipmentId,
            @Valid @RequestBody UserEquipmentUpdateRequest request
    ) {
        return ResponseEntity.ok(userEquipmentService.updateUserEquipment(userDetails.getId(), equipmentId, request));
    }

    @Override
    @DeleteMapping("/{equipmentId}")
    public ResponseEntity<Void> deleteUserEquipment(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long equipmentId
    ) {
        userEquipmentService.deleteUserEquipment(userDetails.getId(), equipmentId);

        return ResponseEntity.noContent().build();
    }
}
