package com.project.picngo.spot.service;

import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.SpotErrorCode;
import com.project.picngo.spot.domain.ChecklistItem;
import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.repository.ChecklistItemRepository;
import com.project.picngo.spot.repository.HiddenChecklistDefaultRepository;
import com.project.picngo.spot.repository.SpotRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChecklistServiceTest {

    @Mock
    private SpotRepository spotRepository;
    @Mock
    private ChecklistItemRepository checklistItemRepository;
    @Mock
    private HiddenChecklistDefaultRepository hiddenChecklistDefaultRepository;

    @InjectMocks
    private ChecklistService checklistService;

    @Test
    @DisplayName("deleteItem은 다른 유저 소유의 항목을 삭제하려 하면 CHECKLIST_ITEM_FORBIDDEN을 던지고 실제로 삭제하지 않는다")
    void deleteItem_rejectsAnotherUsersItem() {
        Long ownerId = 1L;
        Long requesterId = 7L;
        Long spotId = 100L;
        Long itemId = 999L;

        Spot mockSpot = mock(Spot.class);
        given(mockSpot.getId()).willReturn(spotId);

        ChecklistItem mockItem = mock(ChecklistItem.class);
        given(mockItem.getSpot()).willReturn(mockSpot);
        given(mockItem.getUserId()).willReturn(ownerId);
        given(checklistItemRepository.findById(itemId)).willReturn(Optional.of(mockItem));

        assertThatThrownBy(() -> checklistService.deleteItem(spotId, itemId, requesterId))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(SpotErrorCode.CHECKLIST_ITEM_FORBIDDEN);

        verify(checklistItemRepository, never()).delete(any());
    }

    @Test
    @DisplayName("deleteItem은 본인 소유의 항목이면 정상적으로 삭제한다")
    void deleteItem_allowsOwnItem() {
        Long ownerId = 7L;
        Long spotId = 100L;
        Long itemId = 999L;

        Spot mockSpot = mock(Spot.class);
        given(mockSpot.getId()).willReturn(spotId);

        ChecklistItem mockItem = mock(ChecklistItem.class);
        given(mockItem.getSpot()).willReturn(mockSpot);
        given(mockItem.getUserId()).willReturn(ownerId);
        given(checklistItemRepository.findById(itemId)).willReturn(Optional.of(mockItem));

        checklistService.deleteItem(spotId, itemId, ownerId);

        verify(checklistItemRepository).delete(mockItem);
    }
}
