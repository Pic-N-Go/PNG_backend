package com.project.picngo.bookmark.dto;

import com.project.picngo.bookmark.domain.BookmarkCollection;

public record BookmarkCollectionResponse(
        Long id,
        String name,
        String color,
        String icon,
        long spotCount,
        boolean contains
) {
    public static BookmarkCollectionResponse of(BookmarkCollection c, long spotCount, boolean contains) {
        return new BookmarkCollectionResponse(c.getId(), c.getName(), c.getColor(), c.getIcon(), spotCount, contains);
    }
}
