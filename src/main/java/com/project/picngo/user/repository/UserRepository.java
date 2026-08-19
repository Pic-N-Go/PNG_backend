package com.project.picngo.user.repository;

import com.project.picngo.user.domain.Role;
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

	Optional<User> findByProviderAndProviderId(SocialProvider provider, String providerId);

	@Query("SELECT u FROM User u WHERE " +
			"(:keyword IS NULL OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(u.nickname) LIKE LOWER(CONCAT('%', :keyword, '%'))) AND " +
			"(:role IS NULL OR u.role = :role)")
	Page<User> searchUsersForAdmin(@Param("keyword") String keyword, @Param("role") Role role, Pageable pageable);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select u from User u where u.id = :userId")
	Optional<User> findByIdForUpdate(@Param("userId") Long userId);
}
