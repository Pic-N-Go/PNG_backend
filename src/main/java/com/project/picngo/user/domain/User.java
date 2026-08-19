package com.project.picngo.user.domain;

import com.project.picngo.common.domain.BaseTimeEntity;
import com.project.picngo.common.domain.SpotCategory;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.HashSet;
import java.util.Set;

@Getter
@Entity
@Table(name = "users",
	uniqueConstraints = {
			@UniqueConstraint(
					name = "uk_users_provider_provider_id",
					columnNames = {"provider", "provider_id"}
			)
	}
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 100)
	private String email;

	@Column(length = 255)
	private String password;

	/**
	 * 유니크다 — 서비스 계층의 existsByNickname 검사만으로는 동시 가입 경합을 막지 못한다
	 * (검사와 INSERT 사이에 다른 요청이 같은 값을 넣을 수 있다).
	 */
	@Column(nullable = false, unique = true, length = 50)
	private String nickname;

	/** 사용자가 앱에서 올린 프로필 사진의 S3 objectKey. 안 올렸으면 null이다. */
	@Column(length = 500)
	private String profileImageUrl;

	/**
	 * 소셜 로그인이 준 프로필 사진 URL. 로그인할 때마다 갱신되며, 사용자가 직접 올린 사진이
	 * 없을 때의 표시값이다. 두 값을 한 컬럼에 섞으면 사진을 올리는 순간 소셜 사진이 사라져
	 * 되돌릴 수 없다.
	 */
	@Column(length = 500)
	private String socialProfileImageUrl;

	// 자기소개. 프로필 수정에서만 채워지고 가입 시에는 비어 있어 빌더 인자로 두지 않는다.
	@Column(length = 100)
	private String bio;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Role role;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private SocialProvider provider;

	@Column(name = "provider_id", length = 100)
	private String providerId;

	@ElementCollection(fetch = FetchType.LAZY)
	@CollectionTable(
			name = "user_spot_categories",
			joinColumns = @JoinColumn(name = "user_id")
	)
	@Enumerated(EnumType.STRING)
	// Spot.categories와 동일 — 네이티브 ENUM 컬럼 생성 방지. 자세한 근거는 Spot.categories 주석 참고.
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "category", length = 50)
	private Set<SpotCategory> spotCategories = new HashSet<>();

	@Builder
	private User(
		String email,
		String password,
		String nickname,
		String socialProfileImageUrl,
		Role role,
		SocialProvider provider,
		String providerId
	) {
		this.email = email;
		this.password = password;
		this.nickname = nickname;
		this.socialProfileImageUrl = socialProfileImageUrl;
		this.role = role;
		this.provider = provider;
		this.providerId = providerId;
	}

	public static User createLocalUser(
			String email,
			String encodedPassword,
			String nickname,
			Set<SpotCategory> spotCategories
	) {
		User user = User.builder()
				.email(email)
				.password(encodedPassword)
				.nickname(nickname)
				.role(Role.USER)
				.provider(SocialProvider.LOCAL)
				.build();

		user.updateSpotCategories(spotCategories);
		return user;
	}

	public void updateSpotCategories(Set<SpotCategory> spotCategories) {
		this.spotCategories.clear();

		if (spotCategories != null) {
			this.spotCategories.addAll(spotCategories);
		}
	}

	public static User createSocialUser(
		String email,
		String nickname,
		String socialProfileImageUrl,
		SocialProvider provider,
		String providerId
	) {
		return User.builder()
			.email(email)
			.nickname(nickname)
			.socialProfileImageUrl(socialProfileImageUrl)
			.role(Role.USER)
			.provider(provider)
			.providerId(providerId)
			.build();
	}

	/**
	 * 재로그인 시 소셜 프로필 동기화. 닉네임은 일부러 받지 않는다 — 덮어쓰면 사용자가
	 * 프로필 편집에서 정한 닉네임이 매 로그인마다 카카오 이름으로 원복된다.
	 */
	/**
	 * 재로그인 시 소셜 사진 동기화. 사용자가 올린 사진과 다른 칸이라 덮어써도 안전하다.
	 * null이면 그대로 둔다 — 카카오 프로필 사진 제공은 선택 동의라 미동의 사용자는 매 로그인마다
	 * null이 온다. 덮으면 이미 있던 사진이 로그인할 때마다 지워진다.
	 */
	public void updateSocialProfile(String socialProfileImageUrl) {
		if (socialProfileImageUrl != null) {
			this.socialProfileImageUrl = socialProfileImageUrl;
		}
	}

	/** 앱에서 직접 올린 사진이 있는지. 저장소에서 지울 대상인지를 이 값으로 판단한다. */
	public boolean hasUploadedProfileImage() {
		return this.profileImageUrl != null;
	}

	/**
	 * 화면에 보여줄 프로필 사진. 직접 올린 것이 우선이고, 없으면 소셜에서 받은 것으로 떨어진다.
	 * 그래서 올린 사진을 지우면 카카오 사진으로 자연스럽게 되돌아간다.
	 */
	public String getDisplayProfileImage() {
		return profileImageUrl != null ? profileImageUrl : socialProfileImageUrl;
	}

	/** 프로필 사진 교체·삭제. objectKey를 그대로 담는다(응답 직전에 presigned URL로 바꾼다). */
	public void updateProfileImage(String objectKey) {
		this.profileImageUrl = objectKey;
	}

	public void updatePassword(String encodedPassword) {
		this.password = encodedPassword;
	}

	// PUT /users/me는 전체 교체라 null이 오면 비운다. 항목별 부분 수정이 필요해지면 PATCH를 따로 둘 것.
	/**
	 * 닉네임·자기소개만 바꾼다. 프로필 사진은 여기서 건드리지 않는다 —
	 * 클라이언트가 받은 값은 presigned URL이라, 그대로 되돌려 받아 저장하면
	 * 만료된 URL이 컬럼에 박힌다. 사진은 updateProfileImage 전용 경로로만 바뀐다.
	 */
	public void updateProfile(String nickname, String bio){
		this.nickname = nickname;
		this.bio = bio;
	}
}
