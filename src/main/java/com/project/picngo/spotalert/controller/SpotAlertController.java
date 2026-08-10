package com.project.picngo.spotalert.controller;

import com.project.picngo.spotalert.dto.*;
import com.project.picngo.spotalert.service.SpotAlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.project.picngo.auth.service.CustomUserDetails;
import java.util.List;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/spot-alerts")
@RequiredArgsConstructor
public class SpotAlertController implements SpotAlertControllerApiSpec {

    private final SpotAlertService spotAlertService;
    @GetMapping
    public ResponseEntity<List<SpotAlertSettingResponse>> getSpotAlerts(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(spotAlertService.getSpotAlerts(userDetails.getId()));
    }

    @GetMapping("/{spotId}")
    public ResponseEntity<SpotAlertSettingResponse> getSpotAlertDetail(
            @AuthenticationPrincipal CustomUserDetails userDetails, 
            @PathVariable Long spotId) {
        return ResponseEntity.ok(spotAlertService.getSpotAlertDetail(userDetails.getId(), spotId));
    }

    @PutMapping("/{spotId}")
    public ResponseEntity<SpotAlertSettingResponse> updateSpotAlertSettings(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long spotId,
            @Valid @RequestBody SpotAlertSettingUpdateRequest request) {
        return ResponseEntity.ok(spotAlertService.updateSpotAlertSettings(userDetails.getId(), spotId, request));
    }

    @PatchMapping("/{spotId}/active")
    public ResponseEntity<SpotAlertSettingResponse> updateSpotAlertActive(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long spotId,
            @Valid @RequestBody SpotAlertActiveUpdateRequest request) {
        return ResponseEntity.ok(spotAlertService.updateSpotAlertActive(userDetails.getId(), spotId, request));
    }

    @DeleteMapping("/{spotId}")
    public ResponseEntity<Void> deleteSpotAlert(
            @AuthenticationPrincipal CustomUserDetails userDetails, 
            @PathVariable Long spotId) {
        spotAlertService.deleteSpotAlert(userDetails.getId(), spotId);
        return ResponseEntity.noContent().build();
    }
}

