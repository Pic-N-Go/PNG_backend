package com.project.picngo.user.repository;

import com.project.picngo.user.domain.SocialProvider;
import com.project.picngo.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByEmail(String email);
	List<User> findByIdIn(List<Long> ids);

	boolean existsByEmail(String email);

	boolean existsByNickname(String nickname);

	Optional<User> findByProviderAndProviderId(SocialProvider provider, String providerId);
}
