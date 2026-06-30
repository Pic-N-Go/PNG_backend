package com.project.picngo.user.dto;

import com.project.picngo.user.domain.InterestTheme;

import java.util.Set;

public record InterestThemeUpdateRequest(
        Set<InterestTheme> interestThemes
) {
}
