package com.project.picngo.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

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

	@Column(length = 100)
	private String providerId;

	@Column(nullable = false)
	private LocalDateTime createdAt;

	@Column(nullable = false)
	private LocalDateTime updatedAt;

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

	public static User createLocalUser(String email, String encodedPassword, String nickname) {
		return User.builder()
			.email(email)
			.password(encodedPassword)
			.nickname(nickname)
			.role(Role.USER)
			.provider(SocialProvider.LOCAL)
			.build();
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

	@PrePersist
	protected void onCreate() {
		LocalDateTime now = LocalDateTime.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	protected void onUpdate() {
		this.updatedAt = LocalDateTime.now();
	}
}
