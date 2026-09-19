package com.project.picngo.spot.service;

import com.project.picngo.external.dto.AccessibilityTourDetailResponse;
import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.domain.SpotAccessibilityInfo;
import com.project.picngo.spot.repository.SpotAccessibilityInfoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SpotAccessibilityInfoUpsertService {

    private final SpotAccessibilityInfoRepository repository;

    @Transactional
    public void upsert(Spot spot, AccessibilityTourDetailResponse.Item detail) {
        SpotAccessibilityInfo info = repository.findBySpotId(spot.getId())
                .orElseGet(() -> new SpotAccessibilityInfo(spot));
        info.update(detail);
        repository.save(info);
    }
}
