package com.project.picngo.spot.dto;

import com.project.picngo.spot.domain.ChecklistItem;

import java.util.List;

public record ChecklistResponse(
        List<ChecklistItemDto> defaultItems,
        List<ChecklistItemDto> userItems
) {
    public record ChecklistItemDto(Long id, String content) {
        public static ChecklistItemDto from(ChecklistItem item) {
            return new ChecklistItemDto(item.getId(), item.getContent());
        }
    }
}
