package com.project.picngo.auth.dto;

import com.project.picngo.common.domain.SpotCategory;

import static com.project.picngo.common.util.ValidationRules.NICKNAME_MESSAGE;
import static com.project.picngo.common.util.ValidationRules.NICKNAME_REGEX;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
	@Pattern(regexp = NICKNAME_REGEX, message = NICKNAME_MESSAGE)
	String nickname,

	Set<SpotCategory> spotCategories
) {
}
