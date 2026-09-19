package com.project.picngo.spot.service;

import com.project.picngo.external.dto.PetTourDetailResponse;
import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.domain.SpotPetInfo;
import com.project.picngo.spot.repository.SpotPetInfoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SpotPetInfoUpsertService {

    private final SpotPetInfoRepository spotPetInfoRepository;

    @Transactional
    public void upsert(Spot spot, PetTourDetailResponse.Item item) {
        SpotPetInfo petInfo = spotPetInfoRepository.findBySpotId(spot.getId())
                .orElseGet(() -> new SpotPetInfo(spot));
        petInfo.update(item);
        spotPetInfoRepository.save(petInfo);
    }
}
