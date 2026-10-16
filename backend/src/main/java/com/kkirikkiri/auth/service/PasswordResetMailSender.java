package com.kkirikkiri.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import com.kkirikkiri.global.exception.CustomException;
import com.kkirikkiri.global.exception.ErrorCode;

@Service
public class PasswordResetMailSender {

	private final JavaMailSender mailSender;
	private final String from;
	private final AuthMailTelemetry telemetry;

	public PasswordResetMailSender(JavaMailSender mailSender, AuthMailTelemetry telemetry,
			@Value("${auth.password-reset.mail-from:${auth.email-verification.mail-from:}}") String from) {
		this.mailSender = mailSender;
		this.telemetry = telemetry;
		this.from = from;
	}

	public void send(String email, String token, long expiresInSeconds) {
		if (from.isBlank()) {
			telemetry.failure("password-reset", "configuration");
			throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
		}
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(from);
		message.setTo(email);
		message.setSubject("[끼리끼리] 비밀번호 재설정");
		message.setText("비밀번호 재설정 토큰: " + token + "\n유효시간: " + expiresInSeconds
				+ "초\n직접 요청하지 않았다면 이 메일을 무시해 주세요.");
		try {
			mailSender.send(message);
			telemetry.success("password-reset");
		} catch (MailException exception) {
			telemetry.failure("password-reset", exception);
			throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
		}
	}
}
