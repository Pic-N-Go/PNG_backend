package com.project.picngo.auth.dto;

public record EmailVerificationResponse(
	String email,
	boolean verified,
	Long expiresIn,
	String verificationCode
) {

	public static EmailVerificationResponse issued(String email, Long expiresIn, String verificationCode) {
		return new EmailVerificationResponse(email, false, expiresIn, verificationCode);
	}

	public static EmailVerificationResponse verified(String email) {
		return new EmailVerificationResponse(email, true, null, null);
	}
}
