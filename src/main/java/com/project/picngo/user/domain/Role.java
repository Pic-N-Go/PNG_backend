package com.project.picngo.user.domain;

public enum Role {
	USER,
	ADMIN;

	public String getAuthority() {
		return "ROLE_" + name();
	}
}
