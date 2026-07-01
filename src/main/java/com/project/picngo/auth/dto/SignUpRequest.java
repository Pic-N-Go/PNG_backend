package com.project.picngo.auth.dto;

import com.project.picngo.common.domain.SpotCategory;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record SignUpRequest(
	@NotBlank
	@Email
	String email,

	@NotBlank
	@Size(min = 8, max = 64)
	String password,

	@NotBlank
	@Size(max = 50)
	String nickname,

	Set<SpotCategory> spotCategories
) {
}
