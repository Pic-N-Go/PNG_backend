package com.project.picngo.user.service;

import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.AuthErrorCode;
import com.project.picngo.common.exception.code.UserErrorCode;
import com.project.picngo.common.domain.SpotCategory;
import com.project.picngo.user.domain.Follow;
import com.project.picngo.user.domain.SocialProvider;
import com.project.picngo.user.domain.User;
import com.project.picngo.user.dto.*;
import com.project.picngo.user.repository.FollowRepository;
import com.project.picngo.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

	private final UserRepository userRepository;
	private final FollowRepository followRepository;

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

	// 내 프로필 수정
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

	// 타 유저 프로필 조회
	public UserProfileResponse getUserProfile(Long userId) {
		// 타 유저 프로필은 공개 가능한 정보만 응답
		return UserProfileResponse.from(getById(userId));
	}

	// 팔로우
	@Transactional
	public void follow(Long followerId, Long followingId) {
		User follower = getById(followerId);
		User following = getById(followingId);

		// 자기 자신을 팔로우 할 수 없다.
		if(follower.getId().equals(following.getId())) {
			throw new CustomException(UserErrorCode.CANNOT_FOLLOW_SELF);
		}

		// 이미 팔로우한 사용자는 다시 팔로우할 수 없다.
		if(followRepository.existsByFollowerAndFollowing(follower, following)) {
			throw new CustomException(UserErrorCode.ALREADY_FOLLOWING);
		}

		followRepository.save(Follow.create(follower, following));
	}

	// 언팔로우
	@Transactional
	public void unfollow(Long followerId, Long followingId) {
		User follower = getById(followerId);
		User following = getById(followingId);

		Follow follow = followRepository.findByFollowerAndFollowing(follower, following)
				.orElseThrow(()-> new CustomException(UserErrorCode.FOLLOW_NOT_FOUND));

		followRepository.delete(follow);
	}

	// 팔로워 목록 조회
	public List<FollowUserResponse> getFollowers(Long userId) {
		User user = getById(userId);

		return followRepository.findAllByFollowing(user).stream()
				.map(follow -> FollowUserResponse.from(follow.getFollower()))
				.toList();
	}

	// 팔로잉 목록 조회
	public List<FollowUserResponse> getFollowing(Long userId) {
		User user = getById(userId);

		return followRepository.findAllByFollower(user).stream()
				.map(follow -> FollowUserResponse.from(follow.getFollowing()))
				.toList();
	}

    // 내 활동 통계 조회 서비스
    public UserStatsResponse getMyStats(Long userId){
        User user = getById(userId);

        long followerCount = followRepository.countByFollowing(user);
        long followingCount = followRepository.countByFollower(user);

        // TODO: 리뷰/방문 장소 기준은 아직 확정되지 않아 임시로 0을 반환한다
        long reviewCount = 0;
        long visitedSpotCount = 0;

        return new UserStatsResponse(
                followerCount,
                followingCount,
                reviewCount,
                visitedSpotCount
        );
    }
}
