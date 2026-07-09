package com.project.picngo.auth.service;

import com.project.picngo.user.domain.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

public class CustomUserDetails implements UserDetails {

	private final Long id;
	private final String email;
	private final String password;
	private final String nickname;
	private final Collection<? extends GrantedAuthority> authorities;

	private CustomUserDetails(
		Long id,
		String email,
		String password,
		String nickname,
		Collection<? extends GrantedAuthority> authorities
	) {
		this.id = id;
		this.email = email;
		this.password = password;
		this.nickname = nickname;
		this.authorities = authorities;
	}

	public static CustomUserDetails from(User user, Collection<? extends GrantedAuthority> authorities) {
		return new CustomUserDetails(
			user.getId(),
			user.getEmail(),
			user.getPassword() == null ? "" : user.getPassword(),
			user.getNickname(),
			authorities
		);
	}

	public Long getId() {
		return id;
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return authorities;
	}

	@Override
	public String getPassword() {
		return password;
	}

	@Override
	public String getUsername() {
		return email;
	}

	public String getNickname() {
		return nickname;
	}
}
