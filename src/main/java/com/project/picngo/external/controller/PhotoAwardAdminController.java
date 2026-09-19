package com.project.picngo.external.controller;

import com.project.picngo.auth.service.CustomUserDetails;
import com.project.picngo.external.LegalDongMapper;
import com.project.picngo.spot.dto.PhotoAwardSyncStatusResponse;
import com.project.picngo.spot.producer.PhotoAwardSyncProducer;
import com.project.picngo.spot.service.PhotoAwardSyncStatusManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
public class PhotoAwardAdminController implements PhotoAwardAdminControllerApiSpec {

    private final PhotoAwardSyncProducer photoAwardSyncProducer;
    private final PhotoAwardSyncStatusManager photoAwardSyncStatusManager;

    @Override
    @PostMapping("/admin/photo-award/sync")
    public ResponseEntity<String> syncArea(
            @AuthenticationPrincipal CustomUserDetails adminUserDetails,
            @RequestParam(required = false) Integer lDongRegnCd,
            @RequestParam(required = false) Integer areaCode
    ) {
        Integer targetLdongCd = lDongRegnCd;
        if (targetLdongCd == null && areaCode != null) {
            targetLdongCd = LegalDongMapper.toLdongRegnCd(areaCode);
        }

        if (targetLdongCd == null) {
            return ResponseEntity.badRequest().body("동기화할 대상 지역(lDongRegnCd 또는 areaCode)을 지정해야 합니다.");
        }

        Long adminId = adminUserDetails != null ? adminUserDetails.getId() : null;
        photoAwardSyncProducer.sendAreaSync(targetLdongCd, adminId);

        String regionName = LegalDongMapper.getRegionName(targetLdongCd);
        return ResponseEntity.accepted()
                .body(String.format("사진공모전 수상작 지역(%s, 코드: %d) 동기화 작업이 큐에 등록되었습니다. 백그라운드에서 진행됩니다.",
                        regionName, targetLdongCd));
    }

    @Override
    @PostMapping("/admin/photo-award/sync/all")
    public ResponseEntity<String> syncAll(
            @AuthenticationPrincipal CustomUserDetails adminUserDetails
    ) {
        Long adminId = adminUserDetails != null ? adminUserDetails.getId() : null;
        photoAwardSyncProducer.sendAllSync(adminId);
        return ResponseEntity.accepted()
                .body("전국 17개 지역 사진공모전 전체 동기화 작업이 큐에 등록되었습니다. 백그라운드에서 진행됩니다.");
    }

    @Override
    @GetMapping("/admin/photo-award/sync/status")
    public ResponseEntity<PhotoAwardSyncStatusResponse> getSyncStatus() {
        return ResponseEntity.ok(photoAwardSyncStatusManager.getStatus());
    }
}
