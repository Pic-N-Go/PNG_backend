package com.project.picngo.spot.service;

import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.SpotErrorCode;
import com.project.picngo.spot.dto.SpotPetInfoResponse;
import com.project.picngo.spot.repository.SpotPetInfoRepository;
import com.project.picngo.spot.repository.SpotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SpotPetInfoService {

    private final SpotRepository spotRepository;
    private final SpotPetInfoRepository spotPetInfoRepository;

    @Transactional(readOnly = true)
    public Optional<SpotPetInfoResponse> getPetInfo(Long spotId) {
        if (!spotRepository.existsById(spotId)) {
            throw new CustomException(SpotErrorCode.SPOT_NOT_FOUND);
        }
        return spotPetInfoRepository.findBySpotId(spotId)
                .map(SpotPetInfoResponse::from);
    }
}
