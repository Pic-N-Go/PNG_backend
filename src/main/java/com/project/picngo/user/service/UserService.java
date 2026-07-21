package com.project.picngo.user.service;

import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.AuthErrorCode;
import com.project.picngo.common.exception.code.UserErrorCode;
import com.project.picngo.common.domain.SpotCategory;
import com.project.picngo.user.domain.SocialProvider;
import com.project.picngo.user.domain.User;
import com.project.picngo.user.dto.UserProfileResponse;
import com.project.picngo.user.dto.UserProfileUpdateRequest;
import com.project.picngo.user.dto.UserResponse;
import com.project.picngo.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

	private final UserRepository userRepository;

	public User getByEmail(String email) {
		return userRepository.findByEmail(email)
			.orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
	}

	public User getById(Long userId) {
		return userRepository.findById(userId)
			.orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
	}

	public boolean existsByEmail(String email) {
		return userRepository.existsByEmail(email);
	}

	public boolean existsByNickname(String nickname) {
		return userRepository.existsByNickname(nickname);
	}

	public Optional<User> findByEmail(String email){
		return userRepository.findByEmail(email);
	}

	public UserResponse getMyInfo(Long userId) {
		return UserResponse.from(getById(userId));
	}

	@Transactional
	public User createLocalUser(String email, String encodedPassword, String nickname, Set<SpotCategory> spotCategories) {
		if (userRepository.existsByEmail(email)) {
			throw new CustomException(UserErrorCode.EMAIL_ALREADY_EXISTS);
		}

		if (userRepository.existsByNickname(nickname)) {
			throw new CustomException(UserErrorCode.NICKNAME_ALREADY_EXISTS);
		}

		User user = User.createLocalUser(email, encodedPassword, nickname, spotCategories);
		return userRepository.save(user);
	}

	@Transactional
	public User getOrCreateSocialUser(
		String email,
		String nickname,
		String profileImageUrl,
		SocialProvider provider,
		String providerId
	) {
		return userRepository.findByProviderAndProviderId(provider, providerId)
			.map(user -> {
				user.updateSocialProfile(nickname, profileImageUrl);
				return user;
			})
			.orElseGet(() -> {
				if (userRepository.existsByEmail(email)) {
					throw new CustomException(AuthErrorCode.SOCIAL_EMAIL_ALREADY_EXISTS);
				}

				return userRepository.save(User.createSocialUser(email, nickname, profileImageUrl, provider, providerId));
			});
	}

	@Transactional
	public UserResponse updateUserSpotCategories(Long userId, Set<SpotCategory> spotCategories) {
		User user = getById(userId);
		user.updateSpotCategories(spotCategories);
		return UserResponse.from(user);
	}

	@Transactional
	public UserResponse updateMyProfile(Long userId, UserProfileUpdateRequest request){
		User user = getById(userId);

		// 닉네임 변경된 경우 중복 검사 진행
		if(!user.getNickname().equals(request.nickname())
				&& userRepository.existsByNickname(request.nickname())) {
			throw new CustomException(UserErrorCode.NICKNAME_ALREADY_EXISTS);
		}

		user.updateProfile(request.nickname(), request.profileImageUrl());

		return UserResponse.from(user);
	}

	public UserProfileResponse getUserProfile(Long userId) {
		// 타 유저 프로필은 공개 가능한 정보만 응답 
		return UserProfileResponse.from(getById(userId));
	}
}
