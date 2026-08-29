package com.project.picngo.spot.service;

import com.project.picngo.common.domain.SpotCategory;
import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.SpotErrorCode;
import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.domain.enums.SpotStatus;
import com.project.picngo.spot.dto.FestivalResponse;
import com.project.picngo.spot.repository.SpotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FestivalService {

    private final SpotRepository spotRepository;

    public Page<FestivalResponse> getFestivals(String status, LocalDate targetDate, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        LocalDate today = targetDate != null ? targetDate : LocalDate.now();

        LocalDate queryStartDate = null;
        LocalDate queryEndDate = null;

        if ("ONGOING".equalsIgnoreCase(status)) {
            // 현재 진행 중: 시작일 <= today AND 종료일 >= today
            queryStartDate = today;
            queryEndDate = today;
        } else if ("UPCOMING".equalsIgnoreCase(status)) {
            // 예정된 축제: 시작일 > today
            queryStartDate = today.plusDays(1);
        } else if ("ENDED".equalsIgnoreCase(status)) {
            // 종료된 축제: 종료일 < today
            queryEndDate = today.minusDays(1);
        }

        Page<Spot> spots = spotRepository.findFestivals(
                SpotCategory.FESTIVAL,
                SpotStatus.APPROVED,
                queryStartDate,
                queryEndDate,
                pageable
        );

        return spots.map(spot -> FestivalResponse.from(spot, today));
    }

    public FestivalResponse getFestivalById(Long id) {
        Spot spot = spotRepository.findById(id)
                .filter(s -> s.isActive() && s.getStatus() == SpotStatus.APPROVED)
                .orElseThrow(() -> new CustomException(SpotErrorCode.SPOT_NOT_FOUND));

        return FestivalResponse.from(spot);
    }
}
