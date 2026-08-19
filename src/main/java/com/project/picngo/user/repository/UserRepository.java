package com.project.picngo.user.repository;

import com.project.picngo.user.domain.SocialProvider;
import com.project.picngo.user.domain.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByEmail(String email);

	List<User> findByIdIn(List<Long> ids);

	boolean existsByEmail(String email);

	boolean existsByNickname(String nickname);

	/** 프로필 수정용 — 자기 자신은 중복으로 잡히면 안 된다(대소문자만 바꾸는 경우). */
	boolean existsByNicknameAndIdNot(String nickname, Long id);

	Optional<User> findByProviderAndProviderId(SocialProvider provider, String providerId);

	// 사용자 검색 — 닉네임 부분일치. 닉네임이 유니크라 별도 핸들 없이 이것만으로 사람을 찾을 수 있다.
	Page<User> findByNicknameContainingIgnoreCase(String nickname, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :userId")
    Optional<User> findByIdForUpdate(@Param("userId") Long userId);
}
