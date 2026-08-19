package com.project.picngo.auth.service;

import com.project.picngo.user.domain.User;
import com.project.picngo.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

	private final UserRepository userRepository;

	@Override
	public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
		return toUserDetails(requireActive(userRepository.findByEmail(email)));
	}

	public UserDetails loadUserById(Long userId) throws UsernameNotFoundException {
		return toUserDetails(requireActive(userRepository.findById(userId)));
	}

	/**
	 * 탈퇴 계정은 인증 자체를 통과시키지 않는다.
	 *
	 * 서비스 계층(UserService.getById)에서 걸러도 그 메서드를 거치지 않는 경로가 남는다 —
	 * 글쓰기·댓글은 userRepository.findById를 직접 쓴다. 액세스 토큰은 1시간 유효하므로,
	 * 여기서 막지 않으면 탈퇴 직후에도 그 시간 동안 글을 쓸 수 있다.
	 * 소프트 삭제는 빠뜨린 경로 하나가 곧 버그라, 기능마다 검사를 흩뿌리지 않고 여기서 끊는다.
	 */
	private User requireActive(Optional<User> found) {
		User user = found.orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));
		if (user.isWithdrawn()) {
			throw new UsernameNotFoundException("탈퇴한 사용자입니다.");
		}
		return user;
	}

	private UserDetails toUserDetails(User user) {
		return CustomUserDetails.from(
			user,
			List.of(new SimpleGrantedAuthority(user.getRole().getAuthority()))
		);
	}
}
