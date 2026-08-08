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

    private static final int MAX_USER_ITEMS = 10;

    private final SpotRepository spotRepository;
    private final ChecklistItemRepository checklistItemRepository;
    private final HiddenChecklistDefaultRepository hiddenChecklistDefaultRepository;

    public ChecklistResponse getChecklist(Long spotId, Long userId) {
        Spot spot = spotRepository.findById(spotId)
                .orElseThrow(() -> new CustomException(SpotErrorCode.SPOT_NOT_FOUND));

        Set<Integer> hiddenIds = hiddenChecklistDefaultRepository.findBySpotIdAndUserId(spotId, userId)
                .stream()
                .map(HiddenChecklistDefault::getDefaultItemId)
                .collect(Collectors.toSet());

        List<String> presets = ChecklistMapper.getChecklist(spot.getCat3());
        List<DefaultChecklistItemDto> defaultItems = IntStream.range(0, presets.size())
                .mapToObj(i -> new DefaultChecklistItemDto(i + 1, presets.get(i)))
                .filter(dto -> !hiddenIds.contains(dto.defaultItemId()))
                .toList();

        List<ChecklistItemDto> userItems = checklistItemRepository
                .findBySpotIdAndUserIdOrderByOrderIndex(spotId, userId)
                .stream()
                .map(ChecklistItemDto::from)
                .toList();

        return new ChecklistResponse(defaultItems, userItems);
    }

    @Transactional
    public void hideDefaultItem(Long spotId, Integer defaultItemId, Long userId) {
        Spot spot = spotRepository.findById(spotId)
                .orElseThrow(() -> new CustomException(SpotErrorCode.SPOT_NOT_FOUND));
        validateDefaultItemId(spot, defaultItemId);

        // ponytail: try-insert-then-catch-constraint-violation을 시도했으나 Hibernate flush 실패 후
        // 세션이 unusable 상태가 되어 캐치해도 500이 나는 걸 확인함.
        // exists-check-then-insert로 되돌림 — 동시 중복 요청 레이스는 real-user 환경에서도
        // 요청자 본인 세션 내 중복일 뿐이라 (user_id, spot_id, default_item_id) 유니크 제약이 최종 방어.
        if (!hiddenChecklistDefaultRepository.existsBySpotIdAndUserIdAndDefaultItemId(spotId, userId, defaultItemId)) {
            hiddenChecklistDefaultRepository.save(HiddenChecklistDefault.builder()
                    .spot(spot)
                    .userId(userId)
                    .defaultItemId(defaultItemId)
                    .build());
        }
    }

    @Transactional
    public void restoreDefaultItem(Long spotId, Integer defaultItemId, Long userId) {
        Spot spot = spotRepository.findById(spotId)
                .orElseThrow(() -> new CustomException(SpotErrorCode.SPOT_NOT_FOUND));
        validateDefaultItemId(spot, defaultItemId);

        hiddenChecklistDefaultRepository.deleteBySpotIdAndUserIdAndDefaultItemId(spotId, userId, defaultItemId);
    }

    private void validateDefaultItemId(Spot spot, Integer defaultItemId) {
        int presetCount = ChecklistMapper.getChecklist(spot.getCat3()).size();
        if (defaultItemId == null || defaultItemId < 1 || defaultItemId > presetCount) {
            throw new CustomException(SpotErrorCode.CHECKLIST_ITEM_NOT_FOUND);
        }
    }

    @Transactional
    public ChecklistItemDto addItem(Long spotId, ChecklistRequest request, Long userId) {
        Spot spot = spotRepository.findById(spotId)
                .orElseThrow(() -> new CustomException(SpotErrorCode.SPOT_NOT_FOUND));

        List<ChecklistItem> userItems = checklistItemRepository.findBySpotIdAndUserIdOrderByOrderIndex(spotId, userId);
        if (userItems.size() >= MAX_USER_ITEMS) {
            throw new CustomException(SpotErrorCode.CHECKLIST_LIMIT_EXCEEDED);
        }

        int nextOrderIndex = userItems.stream()
                .mapToInt(ChecklistItem::getOrderIndex)
                .max()
                .orElse(-1) + 1;

        ChecklistItem item = ChecklistItem.builder()
                .spot(spot)
                .userId(userId)
                .content(request.content())
                .orderIndex(nextOrderIndex)
                .build();

        return ChecklistItemDto.from(checklistItemRepository.save(item));
    }

    @Transactional
    public void deleteItem(Long spotId, Long itemId, Long userId) {
        ChecklistItem item = checklistItemRepository.findById(itemId)
                .orElseThrow(() -> new CustomException(SpotErrorCode.CHECKLIST_ITEM_NOT_FOUND));

        if (!item.getSpot().getId().equals(spotId) || !userId.equals(item.getUserId())) {
            throw new CustomException(SpotErrorCode.CHECKLIST_ITEM_FORBIDDEN);
        }

        checklistItemRepository.delete(item);
    }
}
