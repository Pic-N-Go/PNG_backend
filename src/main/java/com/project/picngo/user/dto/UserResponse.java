package com.project.picngo.user.dto;

import com.project.picngo.common.domain.SpotCategory;
import com.project.picngo.user.domain.Role;
import com.project.picngo.user.domain.SocialProvider;
import com.project.picngo.user.domain.User;

import java.util.Set;

public record UserResponse(
	Long id,
	String email,
	String nickname,
	String profileImageUrl,
	String bio,
	Role role,
	SocialProvider provider,
	Set<SpotCategory> spotCategories
) {

	public static UserResponse from(User user) {
		return new UserResponse(
			user.getId(),
			user.getEmail(),
			user.getNickname(),
			user.getProfileImageUrl(),
			user.getBio(),
			user.getRole(),
			user.getProvider(),
				user.getSpotCategories()
		);
	}
}
