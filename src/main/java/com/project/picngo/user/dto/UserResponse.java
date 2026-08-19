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

	/**
	 * @param profileImageUrl 저장된 값을 그대로 쓰지 말고 ImageStorageService.getPresignedUrl로
	 *                        변환해 넘긴다. S3에 올린 사진은 objectKey로 저장돼 있어 그대로는 열리지 않는다
	 *                        (카카오 등 http URL은 그 메서드가 통과시킨다).
	 */
	public static UserResponse from(User user, String profileImageUrl) {
		return new UserResponse(
			user.getId(),
			user.getEmail(),
			user.getNickname(),
			profileImageUrl,
			user.getBio(),
			user.getRole(),
			user.getProvider(),
				user.getSpotCategories()
		);
	}
}
