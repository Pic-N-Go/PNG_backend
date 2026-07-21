package com.project.picngo.bookmark.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

// 체크된 컬렉션 집합으로 멤버십을 통째 동기화. 빈 배열이면 모든 컬렉션에서 제거.
public record SyncCollectionsRequest(
        @NotNull(message = "collectionIds는 필수입니다. (빈 배열 허용)")
        List<@NotNull(message = "collectionId는 null일 수 없습니다.") Long> collectionIds
) {}
