package com.project.picngo.user.service;

import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.image.dto.ImageUploadResult;
import com.project.picngo.common.image.service.ImageStorageService;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.project.picngo.common.util.ValidationRules;

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
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class UserService {

	private final UserRepository userRepository;
	private final FollowRepository followRepository;
	private final PasswordEncoder passwordEncoder;
	private final ImageStorageService imageStorageService;

	/**
	 * 프로필 사진은 S3 objectKey로 저장돼 있어 그대로는 열리지 않는다 — 응답에 담기 전에
	 * presigned URL로 바꾼다. 카카오처럼 http로 시작하는 값은 getPresignedUrl이 통과시키므로
	 * 두 형태가 같은 컬럼에 섞여 있어도 된다.
	 */
	private String profileImageUrlOf(User user) {
		return imageStorageService.getPresignedUrl(user.getDisplayProfileImage());
	}

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
		User user = getById(userId);
		return UserResponse.from(user, profileImageUrlOf(user));
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

				String resolved = resolveUniqueNickname(sanitizeNickname(nickname, providerId), providerId);
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
			return fallbackNickname(providerId);
		}
		return cleaned;
	}

	/**
	 * 닉네임으로 쓸 글자가 하나도 안 남았을 때의 대체값. providerId는 계정마다 다르므로
	 * 여기서 나온 값은 사실상 겹치지 않는다 — 숫자가 없는 providerId도 해시로 떨어뜨려
	 * 전부 "user"로 수렴하지 않게 한다(그러면 아래 접미사 루프가 상시 동작한다).
	 */
	private String fallbackNickname(String providerId) {
		if (providerId == null || providerId.isBlank()) {
			return "user";
		}
		String digits = providerId.replaceAll("\\D", "");
		if (!digits.isEmpty()) {
			return "user" + (digits.length() > 6 ? digits.substring(digits.length() - 6) : digits);
		}
		// 36진수라 [0-9a-z]만 나온다. 최대 7자까지 나오므로 "user"(4자)와 합쳐 10자를
		// 넘지 않도록 자른다 — 넘으면 NICKNAME_REGEX를 위반한 값이 저장된다.
		String hashed = Integer.toUnsignedString(providerId.hashCode(), 36);
		int room = NICKNAME_MAX - "user".length();
		return "user" + (hashed.length() > room ? hashed.substring(hashed.length() - room) : hashed);
	}

	/**
	 * 이미 쓰이는 닉네임이면 뒤에 숫자를 붙인다. 정상 경로는 온보딩에서 사용자가 직접 고르는
	 * 것이고, 이건 온보딩을 마치지 못한 계정도 유효한 닉네임을 갖게 하는 안전망이다.
	 *
	 * ⚠️ `users.nickname`에 unique 제약이 추가되면 이 사전 검사만으로는 부족하다.
	 * 검사와 INSERT 사이에 다른 요청이 같은 값을 넣을 수 있어, save 지점에서
	 * DataIntegrityViolationException을 잡아 재시도해야 한다.
	 * `createLocalUser`·`updateMyProfile`도 같은 check-then-write 구조다.
	 */
	private String resolveUniqueNickname(String base, String providerId) {
		if (!userRepository.existsByNickname(base)) {
			return base;
		}
		// 순차 조회라 시도 횟수가 곧 쿼리 수다. 98개까지만 훑고 그 뒤는 계정 고유값으로 뛴다.
		for (int suffix = 2; suffix <= 99; suffix++) {
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
		// 여기까지 왔으면 예외를 던지는 대신 계정 고유값으로 떨어진다 — 카카오 로그인 버튼을
		// 누른 사용자에게 "이미 사용 중인 닉네임입니다"(입력한 적도 없는 값)를 돌려줄 수는 없다.
		return fallbackNickname(providerId);
	}

	@Transactional
	public UserResponse updateUserSpotCategories(Long userId, Set<SpotCategory> spotCategories) {
		User user = getById(userId);
		user.updateSpotCategories(spotCategories);
		return UserResponse.from(user, profileImageUrlOf(user));
	}

	// 내 프로필 수정
	@Transactional
	public UserResponse updateMyProfile(Long userId, UserProfileUpdateRequest request){
		User user = getById(userId);

		// 닉네임을 실제로 바꾼 경우에만 형식·중복을 검사한다. 안 바꿨으면 그냥 통과시킨다 —
		// 새 규칙 이전에 만들어진 닉네임(카카오 원본 등)을 가진 계정이 자기소개만 고치려다
		// 막히면 안 된다. 그 계정은 닉네임을 한 번 바꿀 때 자연스럽게 규칙에 맞춰진다.
		if (!user.getNickname().equals(request.nickname())) {
			if (!request.nickname().matches(ValidationRules.NICKNAME_REGEX)) {
				throw new CustomException(UserErrorCode.INVALID_NICKNAME);
			}
			// 자기 자신은 제외한다 — MySQL 기본 collation(utf8mb4_0900_ai_ci)은 대소문자를
			// 구분하지 않아, abc → Abc처럼 대소문자만 바꾸면 자기 행에 매치돼 중복으로 잡힌다.
			if (userRepository.existsByNicknameAndIdNot(request.nickname(), userId)) {
				throw new CustomException(UserErrorCode.NICKNAME_ALREADY_EXISTS);
			}
		}

		user.updateProfile(request.nickname(), request.bio());

		return UserResponse.from(user, profileImageUrlOf(user));
	}

	/**
	 * 설정에서 비밀번호 변경. 이메일 코드로 재설정하는 `/auth/password/reset`과 달리
	 * 이미 로그인한 사용자가 현재 비밀번호를 확인받고 바꾼다.
	 *
	 * 소셜 계정은 비밀번호가 없어(password null) 이 경로를 쓸 수 없다. 여기서 막지 않으면
	 * matches(raw, null)이 터지거나 소셜 계정에 비밀번호가 생겨 이메일 로그인 진입점이 열린다.
	 */
	@Transactional
	public void changePassword(Long userId, PasswordChangeRequest request) {
		User user = getById(userId);

		if (user.getProvider() != SocialProvider.LOCAL || user.getPassword() == null) {
			throw new CustomException(AuthErrorCode.SOCIAL_ACCOUNT_HAS_NO_PASSWORD);
		}
		if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
			throw new CustomException(AuthErrorCode.INVALID_CURRENT_PASSWORD);
		}

		user.updatePassword(passwordEncoder.encode(request.newPassword()));
	}

	/**
	 * 프로필 사진 교체. 게시글·리뷰 사진과 같은 저장소를 쓰고(ImageStorageService), DB에는
	 * objectKey만 담는다 — presigned URL은 만료되므로 저장하면 안 된다.
	 */
	@Transactional
	public UserResponse updateProfileImage(Long userId, MultipartFile image) {
		User user = getById(userId);
		String previousKey = user.getProfileImageUrl();

		ImageUploadResult uploaded = imageStorageService.upload(image, "profile/" + userId);
		user.updateProfileImage(uploaded.key());

		// 새 사진이 올라간 뒤에 지운다 — 먼저 지우면 업로드가 실패했을 때 사진 없는 계정이 된다.
		// 소셜 사진은 다른 칸에 있어 여기서 지워지지 않는다.
		deletePreviousImage(previousKey);

		return UserResponse.from(user, uploaded.url());
	}

	/**
	 * 올린 프로필 사진 삭제. 소셜 계정이면 카카오 사진으로 되돌아간다 —
	 * 소셜 사진은 별도 칸에 남아 있어 표시값이 자연스럽게 그쪽으로 떨어진다.
	 */
	@Transactional
	public UserResponse deleteProfileImage(Long userId) {
		User user = getById(userId);
		String previousKey = user.getProfileImageUrl();

		user.updateProfileImage(null);
		deletePreviousImage(previousKey);

		return UserResponse.from(user, profileImageUrlOf(user));
	}

	/** 저장소에서 지우는 데 실패해도 사용자 동작은 성공이다 — 남은 객체는 정리 작업의 몫이다. */
	private void deletePreviousImage(String objectKey) {
		if (objectKey == null) {
			return;
		}
		try {
			imageStorageService.delete(objectKey);
		} catch (RuntimeException e) {
			log.warn("이전 프로필 사진 삭제 실패 (key={}): {}", objectKey, e.getMessage());
		}
	}

	// 타 유저 프로필 조회
	public UserProfileResponse getUserProfile(Long userId) {
		// 타 유저 프로필은 공개 가능한 정보만 응답
		User user = getById(userId);
		return UserProfileResponse.from(
				user,
				profileImageUrlOf(user),
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
				.map(follow -> FollowUserResponse.from(follow.getFollower(), profileImageUrlOf(follow.getFollower())))
				.toList();
	}

	// 팔로잉 목록 조회
	public List<FollowUserResponse> getFollowing(Long userId) {
		User user = getById(userId);

		return followRepository.findAllByFollower(user).stream()
				.map(follow -> FollowUserResponse.from(follow.getFollowing(), profileImageUrlOf(follow.getFollowing())))
				.toList();
	}

	// 사용자 검색 — 팔로우할 사람을 찾는 경로다. 응답은 팔로워·팔로잉 목록과 같은 DTO를 쓴다(항목이 동일).
	public Page<FollowUserResponse> searchUsers(String keyword, int page, int size) {
		if (keyword == null || keyword.isBlank()) {
			throw new CustomException(UserErrorCode.SEARCH_KEYWORD_REQUIRED);
		}

		return userRepository
				.findByNicknameContainingIgnoreCase(keyword.trim(), PageRequest.of(page, size))
				.map(user -> FollowUserResponse.from(user, profileImageUrlOf(user)));
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
