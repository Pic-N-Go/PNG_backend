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
import java.time.LocalDateTime;
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

	/**
	 * 탈퇴 시각. null이면 정상 계정이다.
	 *
	 * 탈퇴는 소프트 삭제다 — row를 지우면 FK 때문에 게시글·댓글이 딸려 나가고, 남이 쓴 글에
	 * 달린 대화가 뒤늦게 끊긴다. 대신 이 값이 있으면 로그인·조회에서 제외하고,
	 * 유예 기간이 지나면 개인정보만 파기한다(purgePersonalData).
	 *
	 * 유예 기간에는 개인정보를 그대로 남긴다 — 마스킹하면 복구할 원본이 사라진다.
	 */
	@Column(name = "deleted_at")
	private LocalDateTime deletedAt;

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

	// ── 탈퇴 / 복구 / 파기 ──────────────────────────────────────────

	/**
	 * 파기된 계정의 표시 이름 접두사. nickname은 NOT NULL이고 유니크라 빈 값도, 고정값도 넣을 수 없다
	 * — 고정값이면 두 번째 계정을 파기할 때 제약 위반으로 배치가 통째로 실패한다.
	 * 그래서 뒤에 id를 붙여 유일하게 만든다(작성자 id는 게시글 응답에 이미 실려 있어 새로 드러나는 정보가 없다).
	 *
	 * 공백이 들어 있어 살아 있는 사용자와 절대 겹치지 않는다 — NICKNAME_REGEX가
	 * `^[가-힣a-zA-Z0-9]{2,10}$`라 공백을 허용하지 않으므로 이 이름을 직접 가질 수 없다.
	 */
	public static final String PURGED_NICKNAME_PREFIX = "탈퇴한 사용자 ";

	public boolean isWithdrawn() {
		return this.deletedAt != null;
	}

	public void withdraw(LocalDateTime now) {
		this.deletedAt = now;
	}

	public void restore() {
		this.deletedAt = null;
	}

	/** 유예 기간이 남았는지. 지난 계정은 복구할 수 없고 파기 배치의 대상이 된다. */
	public boolean isRestorableAt(LocalDateTime now, int graceDays) {
		return this.deletedAt != null && this.deletedAt.plusDays(graceDays).isAfter(now);
	}

	/**
	 * 개인정보 파기. row는 남긴다 — 게시글·댓글이 "탈퇴한 사용자"로 계속 보이려면
	 * 작성자 row가 있어야 한다.
	 *
	 * email은 unique NOT NULL이라 빈 값을 넣을 수 없어 id로 유일한 자리표를 만든다.
	 * providerId까지 지워야 같은 카카오 계정으로 새로 가입할 수 있다.
	 */
	public void purgePersonalData() {
		this.email = "deleted_" + this.id + "@deleted.local";
		this.nickname = PURGED_NICKNAME_PREFIX + this.id;
		this.password = null;
		this.providerId = null;
		this.profileImageUrl = null;
		this.socialProfileImageUrl = null;
		this.bio = null;
		this.spotCategories.clear();
	}

	/** 파기 배치가 이미 처리한 계정인지. 배치를 여러 번 돌려도 안전하도록 이 값으로 걸러낸다. */
	public boolean isPurged() {
		return this.nickname != null && this.nickname.startsWith(PURGED_NICKNAME_PREFIX)
				&& this.password == null && this.providerId == null;
	}
}
