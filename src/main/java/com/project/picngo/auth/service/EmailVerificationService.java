package com.project.picngo.auth.service;

import com.project.picngo.auth.domain.EmailVerificationPurpose;
import com.project.picngo.auth.dto.EmailVerificationResponse;
import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.AuthErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {

	private static final long EXPIRATION_SECONDS = 300L;
	private static final SecureRandom RANDOM = new SecureRandom();

	private final JavaMailSender mailSender;
	private final Clock clock = Clock.systemDefaultZone();
	private final Map<String, VerificationCode> verificationCodes = new ConcurrentHashMap<>();
	private final Map<String, Instant> verifiedEmails = new ConcurrentHashMap<>();

	public EmailVerificationResponse issueCode(String email, EmailVerificationPurpose purpose) {
		String normalizedEmail = normalize(email);
		String code = createCode();
		Instant expiresAt = Instant.now(clock).plusSeconds(EXPIRATION_SECONDS);

		verificationCodes.put(normalizedEmail, new VerificationCode(code, expiresAt));
		sendVerificationEmail(normalizedEmail, code, purpose);

		return EmailVerificationResponse.issued(normalizedEmail, EXPIRATION_SECONDS, null);
	}

	private void sendVerificationEmail(String email, String code, EmailVerificationPurpose purpose) {
		SimpleMailMessage message = new SimpleMailMessage();
		message.setTo(email);

		if(purpose == EmailVerificationPurpose.SIGN_UP) {
			message.setSubject("[PicnGo] 이메일 인증 코드");
			message.setText("인증 코드는 " + code + " 입니다.\n5분 안에 입력해주세요!");
		} else if(purpose == EmailVerificationPurpose.PASSWORD_RESET) {
			message.setSubject("[PicnGo] 비밀번호 재설정 인증 코드");
			message.setText("인증 코드는 " + code + " 입니다.\n5분 안에 입력해주세요!");
		}

		mailSender.send(message);
	}

	public EmailVerificationResponse confirmCode(String email, String code) {
		String normalizedEmail = normalize(email);
		VerificationCode verificationCode = verificationCodes.get(normalizedEmail);

		if (verificationCode == null || verificationCode.isExpired(Instant.now(clock))) {
			verificationCodes.remove(normalizedEmail);
			throw new CustomException(AuthErrorCode.EMAIL_VERIFICATION_CODE_NOT_FOUND);
		}

		if (!verificationCode.matches(code)) {
			throw new CustomException(AuthErrorCode.EMAIL_VERIFICATION_CODE_MISMATCH);
		}

		verificationCodes.remove(normalizedEmail);
		verifiedEmails.put(normalizedEmail, Instant.now(clock));

		return EmailVerificationResponse.verified(normalizedEmail);
	}

	public void validateVerified(String email) {
		String normalizedEmail = normalize(email);

		if (!verifiedEmails.containsKey(normalizedEmail)) {
			throw new CustomException(AuthErrorCode.EMAIL_NOT_VERIFIED);
		}
	}

	private String createCode() {
		return String.format("%06d", RANDOM.nextInt(1_000_000));
	}

	private String normalize(String email) {
		return email.trim().toLowerCase();
	}

	private record VerificationCode(String code, Instant expiresAt) {

		private boolean matches(String inputCode) {
			return code.equals(inputCode);
		}

		private boolean isExpired(Instant now) {
			return now.isAfter(expiresAt);
		}
	}
}
