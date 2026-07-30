package com.project.picngo.user.dto;

import com.project.picngo.common.domain.SpotCategory;
import com.project.picngo.user.domain.User;

import java.util.Set;

public record UserProfileResponse (
        Long id,
        String nickname,
        String profileImageUrl,
        Set<SpotCategory> spotCategories
){
    public static UserProfileResponse from(User user){
        return new UserProfileResponse(
                user.getId(),
                user.getNickname(),
                user.getProfileImageUrl(),
                user.getSpotCategories()
        );
    }
}
