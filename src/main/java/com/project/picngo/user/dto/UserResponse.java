package com.project.picngo.user.dto;

import com.project.picngo.user.domain.Role;
import com.project.picngo.user.domain.SocialProvider;
import com.project.picngo.user.domain.User;

public record UserResponse(
	Long id,
	String email,
	String nickname,
	String profileImageUrl,
	Role role,
	SocialProvider provider
) {

	public static UserResponse from(User user) {
		return new UserResponse(
			user.getId(),
			user.getEmail(),
			user.getNickname(),
			user.getProfileImageUrl(),
			user.getRole(),
			user.getProvider()
		);
	}
}
