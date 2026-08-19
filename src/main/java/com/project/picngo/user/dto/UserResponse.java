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
	/**
	 * 소셜에서 받은 프로필 사진. 올린 사진을 지웠을 때 되돌아갈 값이라 클라이언트가 미리보기에
	 * 쓴다 — 이게 없으면 삭제 미리보기가 "사진 없음"으로 보이는데 실제로는 이 사진이 나온다.
	 * 본인 응답에만 담긴다(UserResponse는 /users/me·로그인 전용).
	 */
	String socialProfileImageUrl,
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
			// 소셜 사진은 외부 URL이라 presign이 필요 없다(getPresignedUrl도 그대로 통과시킨다).
			user.getSocialProfileImageUrl(),
			user.getBio(),
			user.getRole(),
			user.getProvider(),
				user.getSpotCategories()
		);
	}
}
