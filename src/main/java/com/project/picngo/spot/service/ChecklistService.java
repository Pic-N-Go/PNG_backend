package com.project.picngo.spot.service;

import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.SpotErrorCode;
import com.project.picngo.spot.domain.ChecklistItem;
import com.project.picngo.spot.domain.ChecklistMapper;
import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.dto.ChecklistRequest;
import com.project.picngo.spot.dto.ChecklistResponse;
import com.project.picngo.spot.dto.ChecklistResponse.ChecklistItemDto;
import com.project.picngo.spot.repository.ChecklistItemRepository;
import com.project.picngo.spot.repository.SpotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChecklistService {

    // ponytail: Spring Security 연동 전까지 하드코딩
    private static final Long TEMP_USER_ID = 1L;
    private static final int MAX_USER_ITEMS = 10;

    private final SpotRepository spotRepository;
    private final ChecklistItemRepository checklistItemRepository;

    public ChecklistResponse getChecklist(Long spotId) {
        Spot spot = spotRepository.findById(spotId)
                .orElseThrow(() -> new CustomException(SpotErrorCode.SPOT_NOT_FOUND));

        List<ChecklistItemDto> defaultItems = ChecklistMapper.getChecklist(spot.getCat3())
                .stream()
                .map(content -> new ChecklistItemDto(null, content))
                .toList();

        List<ChecklistItemDto> userItems = checklistItemRepository
                .findBySpotIdAndUserIdOrderByOrderIndex(spotId, TEMP_USER_ID)
                .stream()
                .map(ChecklistItemDto::from)
                .toList();

        return new ChecklistResponse(defaultItems, userItems);
    }

    @Transactional
    public ChecklistItemDto addItem(Long spotId, ChecklistRequest request) {
        Spot spot = spotRepository.findById(spotId)
                .orElseThrow(() -> new CustomException(SpotErrorCode.SPOT_NOT_FOUND));

        int count = checklistItemRepository.countBySpotIdAndUserId(spotId, TEMP_USER_ID);
        if (count >= MAX_USER_ITEMS) {
            throw new CustomException(SpotErrorCode.CHECKLIST_LIMIT_EXCEEDED);
        }

        ChecklistItem item = ChecklistItem.builder()
                .spot(spot)
                .userId(TEMP_USER_ID)
                .content(request.content())
                .orderIndex(count)
                .build();

        return ChecklistItemDto.from(checklistItemRepository.save(item));
    }

    @Transactional
    public void deleteItem(Long spotId, Long itemId) {
        ChecklistItem item = checklistItemRepository.findById(itemId)
                .orElseThrow(() -> new CustomException(SpotErrorCode.CHECKLIST_ITEM_NOT_FOUND));

        if (!item.getSpot().getId().equals(spotId) || !TEMP_USER_ID.equals(item.getUserId())) {
            throw new CustomException(SpotErrorCode.CHECKLIST_ITEM_FORBIDDEN);
        }

        checklistItemRepository.delete(item);
    }
}
