package com.project.picngo.user.dto;

import com.project.picngo.common.domain.SpotCategory;

import java.util.Set;

public record UserSpotCategoryUpdateRequest(
        Set<SpotCategory> spotCategories
) {
}
