package com.project.picngo.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignUpRequest(
	@NotBlank
	@Email
	String email,

	@NotBlank
	@Size(min = 8, max = 64)
	String password,

	@NotBlank
	@Size(max = 50)
	String nickname
) {
}
