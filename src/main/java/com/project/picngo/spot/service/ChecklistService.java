package com.project.picngo.spot.service;

import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.SpotErrorCode;
import com.project.picngo.spot.domain.ChecklistItem;
import com.project.picngo.spot.domain.ChecklistMapper;
import com.project.picngo.spot.domain.HiddenChecklistDefault;
import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.dto.ChecklistRequest;
import com.project.picngo.spot.dto.ChecklistResponse;
import com.project.picngo.spot.dto.ChecklistResponse.ChecklistItemDto;
import com.project.picngo.spot.dto.ChecklistResponse.DefaultChecklistItemDto;
import com.project.picngo.spot.repository.ChecklistItemRepository;
import com.project.picngo.spot.repository.HiddenChecklistDefaultRepository;
import com.project.picngo.spot.repository.SpotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChecklistService {

    // ponytail: Spring Security 연동 전까지 하드코딩
    private static final Long TEMP_USER_ID = 1L;
    private static final int MAX_USER_ITEMS = 10;

    private final SpotRepository spotRepository;
    private final ChecklistItemRepository checklistItemRepository;
    private final HiddenChecklistDefaultRepository hiddenChecklistDefaultRepository;

    public ChecklistResponse getChecklist(Long spotId) {
        Spot spot = spotRepository.findById(spotId)
                .orElseThrow(() -> new CustomException(SpotErrorCode.SPOT_NOT_FOUND));

        Set<Integer> hiddenIds = hiddenChecklistDefaultRepository.findBySpotIdAndUserId(spotId, TEMP_USER_ID)
                .stream()
                .map(HiddenChecklistDefault::getDefaultItemId)
                .collect(Collectors.toSet());

        List<String> presets = ChecklistMapper.getChecklist(spot.getCat3());
        List<DefaultChecklistItemDto> defaultItems = IntStream.range(0, presets.size())
                .mapToObj(i -> new DefaultChecklistItemDto(i + 1, presets.get(i)))
                .filter(dto -> !hiddenIds.contains(dto.defaultItemId()))
                .toList();

        List<ChecklistItemDto> userItems = checklistItemRepository
                .findBySpotIdAndUserIdOrderByOrderIndex(spotId, TEMP_USER_ID)
                .stream()
                .map(ChecklistItemDto::from)
                .toList();

        return new ChecklistResponse(defaultItems, userItems);
    }

    @Transactional
    public void hideDefaultItem(Long spotId, Integer defaultItemId) {
        Spot spot = spotRepository.findById(spotId)
                .orElseThrow(() -> new CustomException(SpotErrorCode.SPOT_NOT_FOUND));
        validateDefaultItemId(spot, defaultItemId);

        // ponytail: try-insert-then-catch-constraint-violation을 시도했으나 Hibernate flush 실패 후
        // 세션이 unusable 상태가 되어 캐치해도 500이 나는 걸 확인함 (동시 중복 요청보다 이 회귀가 더 흔한 케이스).
        // exists-check-then-insert로 되돌림 — 진짜 동시 요청 레이스는 현재 TEMP_USER_ID 단일 유저 환경에서 실질 위험 없음.
        if (!hiddenChecklistDefaultRepository.existsBySpotIdAndUserIdAndDefaultItemId(spotId, TEMP_USER_ID, defaultItemId)) {
            hiddenChecklistDefaultRepository.save(HiddenChecklistDefault.builder()
                    .spot(spot)
                    .userId(TEMP_USER_ID)
                    .defaultItemId(defaultItemId)
                    .build());
        }
    }

    @Transactional
    public void restoreDefaultItem(Long spotId, Integer defaultItemId) {
        Spot spot = spotRepository.findById(spotId)
                .orElseThrow(() -> new CustomException(SpotErrorCode.SPOT_NOT_FOUND));
        validateDefaultItemId(spot, defaultItemId);

        hiddenChecklistDefaultRepository.deleteBySpotIdAndUserIdAndDefaultItemId(spotId, TEMP_USER_ID, defaultItemId);
    }

    private void validateDefaultItemId(Spot spot, Integer defaultItemId) {
        int presetCount = ChecklistMapper.getChecklist(spot.getCat3()).size();
        if (defaultItemId == null || defaultItemId < 1 || defaultItemId > presetCount) {
            throw new CustomException(SpotErrorCode.CHECKLIST_ITEM_NOT_FOUND);
        }
    }

    @Transactional
    public ChecklistItemDto addItem(Long spotId, ChecklistRequest request) {
        Spot spot = spotRepository.findById(spotId)
                .orElseThrow(() -> new CustomException(SpotErrorCode.SPOT_NOT_FOUND));

        List<ChecklistItem> userItems = checklistItemRepository.findBySpotIdAndUserIdOrderByOrderIndex(spotId, TEMP_USER_ID);
        if (userItems.size() >= MAX_USER_ITEMS) {
            throw new CustomException(SpotErrorCode.CHECKLIST_LIMIT_EXCEEDED);
        }

        int nextOrderIndex = userItems.stream()
                .mapToInt(ChecklistItem::getOrderIndex)
                .max()
                .orElse(-1) + 1;

        ChecklistItem item = ChecklistItem.builder()
                .spot(spot)
                .userId(TEMP_USER_ID)
                .content(request.content())
                .orderIndex(nextOrderIndex)
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
