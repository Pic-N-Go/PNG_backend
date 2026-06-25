package com.project.picngo.user.service;

import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.AuthErrorCode;
import com.project.picngo.common.exception.code.UserErrorCode;
import com.project.picngo.user.domain.InterestTheme;
import com.project.picngo.user.domain.SocialProvider;
import com.project.picngo.user.domain.User;
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
	public User createLocalUser(String email, String encodedPassword, String nickname, Set<InterestTheme> interestThemes) {
		if (userRepository.existsByEmail(email)) {
			throw new CustomException(UserErrorCode.EMAIL_ALREADY_EXISTS);
		}

		if (userRepository.existsByNickname(nickname)) {
			throw new CustomException(UserErrorCode.NICKNAME_ALREADY_EXISTS);
		}

		User user = User.createLocalUser(email, encodedPassword, nickname, interestThemes);
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
	public UserResponse updateInterestTheme(Long userId, Set<InterestTheme> interestThemes) {
		User user = getById(userId);
		user.updateInterestThemes(interestThemes);
		return UserResponse.from(user);
	}
}
