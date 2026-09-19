package com.project.picngo.spot.service;

import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.SpotErrorCode;
import com.project.picngo.spot.dto.SpotAccessibilityInfoResponse;
import com.project.picngo.spot.repository.SpotAccessibilityInfoRepository;
import com.project.picngo.spot.repository.SpotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SpotAccessibilityInfoService {

    private final SpotRepository spotRepository;
    private final SpotAccessibilityInfoRepository repository;

    @Transactional(readOnly = true)
    public Optional<SpotAccessibilityInfoResponse> getAccessibilityInfo(Long spotId) {
        if (!spotRepository.existsById(spotId)) {
            throw new CustomException(SpotErrorCode.SPOT_NOT_FOUND);
        }
        return repository.findBySpotId(spotId).map(SpotAccessibilityInfoResponse::from);
    }
}
