package com.kkirikkiri.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import com.kkirikkiri.auth.entity.EmailVerificationPurpose;
import com.kkirikkiri.global.exception.CustomException;
import com.kkirikkiri.global.exception.ErrorCode;

@Service
public class EmailVerificationMailSender {

	private final JavaMailSender mailSender;
	private final String from;
	private final AuthMailTelemetry telemetry;

	public EmailVerificationMailSender(JavaMailSender mailSender, AuthMailTelemetry telemetry,
			@Value("${auth.email-verification.mail-from:}") String from) {
		this.mailSender = mailSender;
		this.telemetry = telemetry;
		this.from = from;
	}

	public void send(String email, EmailVerificationPurpose purpose, String code, long expiresInSeconds) {
		if (from.isBlank()) {
			telemetry.failure("verification", "configuration");
			throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
		}
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(from);
		message.setTo(email);
		message.setSubject("[끼리끼리] 학교 이메일 인증번호");
		message.setText("인증 목적: " + purpose.name() + "\n인증번호: " + code
				+ "\n유효시간: " + expiresInSeconds + "초");
		try {
			mailSender.send(message);
			telemetry.success("verification");
		} catch (MailException exception) {
			telemetry.failure("verification", exception);
			throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
		}
	}
}
