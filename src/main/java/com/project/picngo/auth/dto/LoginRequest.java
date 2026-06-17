package com.project.picngo.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
	@NotBlank
	@Email
	String email,

	@NotBlank
	String password
) {
}
