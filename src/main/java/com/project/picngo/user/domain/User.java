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

	public void updateSocialProfile(String nickname, String profileImageUrl) {
		this.nickname = nickname;
		this.profileImageUrl = profileImageUrl;
	}

	public void updatePassword(String encodedPassword) {
		this.password = encodedPassword;
	}

	public void updateProfile(String nickname, String profileImageUrl){
		this.nickname = nickname;
		this.profileImageUrl = profileImageUrl;
	}

	public void updateRole(Role role) {
		if (role != null) {
			this.role = role;
		}
	}
}
