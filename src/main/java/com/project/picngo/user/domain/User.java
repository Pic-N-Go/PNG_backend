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

	@Column(nullable = false, length = 50)
	private String nickname;

	@Column(length = 500)
	private String profileImageUrl;

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
		String profileImageUrl,
		Role role,
		SocialProvider provider,
		String providerId
	) {
		this.email = email;
		this.password = password;
		this.nickname = nickname;
		this.profileImageUrl = profileImageUrl;
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
		String profileImageUrl,
		SocialProvider provider,
		String providerId
	) {
		return User.builder()
			.email(email)
			.nickname(nickname)
			.profileImageUrl(profileImageUrl)
			.role(Role.USER)
			.provider(provider)
			.providerId(providerId)
			.build();
	}

	/**
	 * 재로그인 시 소셜 프로필 동기화. 닉네임은 일부러 받지 않는다 — 덮어쓰면 사용자가
	 * 프로필 편집에서 정한 닉네임이 매 로그인마다 카카오 이름으로 원복된다.
	 */
	public void updateSocialProfile(String profileImageUrl) {
		this.profileImageUrl = profileImageUrl;
	}

	public void updatePassword(String encodedPassword) {
		this.password = encodedPassword;
	}

	// PUT /users/me는 전체 교체라 null이 오면 비운다. 항목별 부분 수정이 필요해지면 PATCH를 따로 둘 것.
	public void updateProfile(String nickname, String profileImageUrl, String bio){
		this.nickname = nickname;
		this.profileImageUrl = profileImageUrl;
		this.bio = bio;
	}
}
