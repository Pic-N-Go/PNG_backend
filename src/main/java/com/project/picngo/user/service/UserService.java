package com.project.picngo.user.service;

import com.project.picngo.common.exception.CustomException;

import static com.project.picngo.common.util.ValidationRules.NICKNAME_MAX;
import static com.project.picngo.common.util.ValidationRules.NICKNAME_MIN;

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

	/** 소셜 로그인 결과. 신규 가입이면 클라이언트를 온보딩으로 보내야 해서 함께 돌려준다. */
	public record SocialUserResult(User user, boolean newUser) {
	}

	@Transactional
	public SocialUserResult getOrCreateSocialUser(
		String email,
		String nickname,
		String profileImageUrl,
		SocialProvider provider,
		String providerId
	) {
		return userRepository.findByProviderAndProviderId(provider, providerId)
			.map(user -> {
				// 닉네임은 덮지 않는다 — 사용자가 프로필 편집에서 바꾼 값이 매 로그인마다 카카오
				// 이름으로 원복된다. 사진은 카카오가 유일한 출처라 계속 동기화한다
				// (앱 자체 업로드가 생기면 그때 직접 올린 사진을 지키는 분기가 필요하다).
				user.updateSocialProfile(profileImageUrl);
				return new SocialUserResult(user, false);
			})
			.orElseGet(() -> {
				if (userRepository.existsByEmail(email)) {
					throw new CustomException(AuthErrorCode.SOCIAL_EMAIL_ALREADY_EXISTS);
				}

				String resolved = resolveUniqueNickname(sanitizeNickname(nickname, providerId));
				User created = userRepository.save(
					User.createSocialUser(email, resolved, profileImageUrl, provider, providerId));
				return new SocialUserResult(created, true);
			});
	}

	/**
	 * 카카오 닉네임을 우리 규칙(ValidationRules.NICKNAME_REGEX)에 맞게 다듬는다.
	 * 이 경로는 요청 DTO를 거치지 않아 @Pattern이 걸리지 않는다 — 이모지·특수문자·1자·11자
	 * 이상이 그대로 들어오므로 여기서 직접 막지 않으면 규칙 위반 데이터가 DB에 남는다.
	 */
	private String sanitizeNickname(String rawNickname, String providerId) {
		String cleaned = rawNickname == null ? "" : rawNickname.replaceAll("[^가-힣a-zA-Z0-9]", "");
		if (cleaned.length() > NICKNAME_MAX) {
			cleaned = cleaned.substring(0, NICKNAME_MAX);
		}
		// 정제 후 남는 글자가 없거나 너무 짧은 경우(이모지만으로 된 닉네임 등)
		if (cleaned.length() < NICKNAME_MIN) {
			String digits = providerId == null ? "" : providerId.replaceAll("\\D", "");
			String tail = digits.length() > 6 ? digits.substring(digits.length() - 6) : digits;
			return "user" + tail;
		}
		return cleaned;
	}

	/**
	 * 이미 쓰이는 닉네임이면 뒤에 숫자를 붙인다. 정상 경로는 온보딩에서 사용자가 직접 고르는
	 * 것이고, 이건 온보딩을 마치지 못한 계정도 유효한 닉네임을 갖게 하는 안전망이다.
	 */
	private String resolveUniqueNickname(String base) {
		if (!userRepository.existsByNickname(base)) {
			return base;
		}
		for (int suffix = 2; suffix <= 999; suffix++) {
			String tail = String.valueOf(suffix);
			// 접미사를 붙여도 최대 길이를 넘지 않도록 앞을 자른다
			String head = base.length() + tail.length() > NICKNAME_MAX
				? base.substring(0, NICKNAME_MAX - tail.length())
				: base;
			String candidate = head + tail;
			if (!userRepository.existsByNickname(candidate)) {
				return candidate;
			}
		}
		throw new CustomException(UserErrorCode.NICKNAME_ALREADY_EXISTS);
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

		user.updateProfile(request.nickname(), request.profileImageUrl(), request.bio());

		return UserResponse.from(user);
	}

	// 타 유저 프로필 조회
	public UserProfileResponse getUserProfile(Long userId) {
		// 타 유저 프로필은 공개 가능한 정보만 응답
		User user = getById(userId);
		return UserProfileResponse.from(
				user,
				followRepository.countByFollowing(user),
				followRepository.countByFollower(user)
		);
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
