package com.project.picngo.user.domain;

import com.project.picngo.common.domain.SpotCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.project.picngo.common.util.ValidationRules.NICKNAME_REGEX;

/**
 * 회원 탈퇴는 소프트 삭제 → 30일 유예 → 개인정보 파기 순으로 간다.
 * 경계(딱 30일)와 파기 멱등성이 틀리면, 복구할 수 있어야 하는 계정이 파기되거나
 * 파기돼야 하는 개인정보가 남는다. 되돌릴 수 없는 쪽이라 여기서 못 박아둔다.
 */
class UserWithdrawalTest {

    private static final int GRACE = 30;

    private User newUser() {
        User user = User.createLocalUser("a@b.com", "encoded", "테스터", Set.of(SpotCategory.NIGHT_VIEW));
        // purgePersonalData가 id로 유일한 이메일 자리표를 만든다 — DB가 채우는 값이라 직접 넣어준다.
        ReflectionTestUtils.setField(user, "id", 7L);
        return user;
    }

    @Test
    @DisplayName("탈퇴하면 탈퇴 상태가 되고, 복구하면 되돌아온다")
    void withdrawAndRestore() {
        User user = newUser();
        assertFalse(user.isWithdrawn());

        user.withdraw(LocalDateTime.now());
        assertTrue(user.isWithdrawn());

        user.restore();
        assertFalse(user.isWithdrawn());
    }

    @Test
    @DisplayName("유예 기간 경계 — 30일이 지나기 전까지만 복구할 수 있다")
    void restorableWindow() {
        User user = newUser();
        LocalDateTime withdrawnAt = LocalDateTime.of(2026, 8, 1, 12, 0);
        user.withdraw(withdrawnAt);

        assertTrue(user.isRestorableAt(withdrawnAt, GRACE), "탈퇴 직후");
        assertTrue(user.isRestorableAt(withdrawnAt.plusDays(29), GRACE), "29일 뒤");
        // 딱 30일은 복구 불가 — 이 경계가 뒤집히면 파기 배치와 겹쳐 파기된 계정을 되살리려 한다.
        assertFalse(user.isRestorableAt(withdrawnAt.plusDays(30), GRACE), "정확히 30일 뒤");
        assertFalse(user.isRestorableAt(withdrawnAt.plusDays(31), GRACE), "31일 뒤");
    }

    @Test
    @DisplayName("탈퇴하지 않은 계정은 복구 대상이 아니다")
    void notRestorableWhenActive() {
        assertFalse(newUser().isRestorableAt(LocalDateTime.now(), GRACE));
    }

    @Test
    @DisplayName("파기하면 개인정보만 지워지고 계정 row는 남는다")
    void purgeRemovesPersonalDataOnly() {
        User user = newUser();
        user.updateProfileImage("uploaded/key.jpg");
        user.updateSocialProfile("https://kakao/profile.jpg");
        user.updateProfile("테스터", "안녕하세요");
        user.withdraw(LocalDateTime.now());

        user.purgePersonalData();

        // 개인정보는 남아 있으면 안 된다
        assertEquals("deleted_7@deleted.local", user.getEmail(), "이메일은 unique NOT NULL이라 자리표로 대체한다");
        // 닉네임에 유니크 제약이 있어 고정값을 쓸 수 없다 — 고정값이면 두 번째 계정 파기가 실패한다.
        assertEquals(User.PURGED_NICKNAME_PREFIX + "7", user.getNickname());
        assertNull(user.getPassword());
        assertNull(user.getProviderId(), "지워야 같은 소셜 계정으로 새로 가입할 수 있다");
        assertNull(user.getProfileImageUrl());
        assertNull(user.getSocialProfileImageUrl());
        assertNull(user.getBio());
        assertTrue(user.getSpotCategories().isEmpty());

        // 게시글·댓글이 "탈퇴한 사용자"로 계속 보이려면 작성자 row가 살아 있어야 한다
        assertEquals(7L, user.getId());
        assertTrue(user.isWithdrawn(), "파기해도 탈퇴 상태는 유지된다");
    }

    @Test
    @DisplayName("파기 배치를 두 번 돌려도 결과가 같다 — 다중 인스턴스에서 함께 돌 수 있다")
    void purgeIsIdempotent() {
        User user = newUser();
        user.withdraw(LocalDateTime.now());

        assertFalse(user.isPurged(), "파기 전");
        user.purgePersonalData();
        assertTrue(user.isPurged(), "파기 후");

        String emailAfterFirst = user.getEmail();
        user.purgePersonalData();
        assertEquals(emailAfterFirst, user.getEmail());
        assertTrue(user.isPurged());
    }

    @Test
    @DisplayName("서로 다른 계정을 파기해도 닉네임이 겹치지 않는다 — users.nickname에 유니크 제약이 있다")
    void purgedNicknamesAreUnique() {
        User first = newUser();
        User second = User.createLocalUser("c@d.com", "encoded", "테스터2", Set.of());
        ReflectionTestUtils.setField(second, "id", 8L);

        first.purgePersonalData();
        second.purgePersonalData();

        // 고정값을 쓰면 두 번째 파기에서 제약 위반으로 배치 트랜잭션이 통째로 롤백된다.
        assertNotEquals(first.getNickname(), second.getNickname());
        assertNotEquals(first.getEmail(), second.getEmail());
    }

    @Test
    @DisplayName("표시 이름은 파기 전에도 쓰인다 — 30일 동안 원래 닉네임이 노출되면 안 된다")
    void displayNameIsUsedBeforePurge() {
        User user = newUser();
        user.withdraw(LocalDateTime.now());

        // 응답 매핑(PostAuthorResponse·UserProfileResponse)이 이 값으로 치환한다.
        // DB의 닉네임은 그대로 남아 있어야 복구할 수 있다.
        assertTrue(user.isWithdrawn());
        assertEquals("테스터", user.getNickname(), "파기 전에는 DB 값이 유지된다(복구용)");
        assertEquals("탈퇴한 사용자", User.WITHDRAWN_DISPLAY_NAME);
    }

    @Test
    @DisplayName("파기 닉네임은 살아 있는 사용자가 가질 수 없는 형태다")
    void purgedNicknameCannotCollideWithLiveUser() {
        User user = newUser();
        user.purgePersonalData();

        // NICKNAME_REGEX(^[가-힣a-zA-Z0-9]{2,10}$)는 공백을 허용하지 않아 사칭이 불가능하다.
        assertFalse(user.getNickname().matches(NICKNAME_REGEX));
    }

    @Test
    @DisplayName("파기된 계정도 탈퇴 상태를 유지한다 — 비밀번호 재설정 등 진입 경로가 계속 막혀야 한다")
    void purgedUserStaysWithdrawn() {
        User user = newUser();
        user.withdraw(LocalDateTime.now());
        user.purgePersonalData();

        // getByEmail·getById가 isWithdrawn()으로 거르므로 이 값이 살아 있어야 차단이 유지된다.
        assertTrue(user.isWithdrawn());
    }

    @Test
    @DisplayName("정상 계정은 파기된 계정으로 오인되지 않는다")
    void activeUserIsNotPurged() {
        assertFalse(newUser().isPurged(), "비밀번호가 있어 파기 대상과 구별된다");
    }
}
