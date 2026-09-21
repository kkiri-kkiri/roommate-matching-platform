package com.kkirikkiri.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.Optional;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import com.kkirikkiri.auth.controller.EmailVerificationController;
import com.kkirikkiri.auth.entity.EmailVerification;
import com.kkirikkiri.auth.entity.EmailVerificationPurpose;
import com.kkirikkiri.auth.repository.EmailVerificationRepository;
import com.kkirikkiri.auth.service.EmailVerificationMailSender;
import com.kkirikkiri.auth.service.EmailVerificationService;
import com.kkirikkiri.global.config.SecurityConfig;
import com.kkirikkiri.global.security.JwtTokenProvider;
import com.kkirikkiri.user.repository.UserRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = EmailVerificationController.class, properties = {
		"jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
		"auth.email-verification.mail-from=noreply@example.com"
})
@Import({SecurityConfig.class, JwtTokenProvider.class, EmailVerificationService.class,
		EmailVerificationMailSender.class})
class EmailVerificationIntegrationTest {

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper objectMapper;
	@Autowired PasswordEncoder passwordEncoder;
	@MockitoBean EmailVerificationRepository repository;
	@MockitoBean JavaMailSender mailSender;
	@MockitoBean UserRepository userRepository;
	private final AtomicReference<EmailVerification> stored = new AtomicReference<>();
	private final AtomicReference<SimpleMailMessage> mail = new AtomicReference<>();

	@BeforeEach
	void setUp() {
		stored.set(null);
		mail.set(null);
		when(repository.findFirstByEmailAndPurposeOrderByCreatedAtDescVerificationIdDesc(
				"student@hknu.ac.kr", EmailVerificationPurpose.SIGNUP))
				.thenAnswer(invocation -> Optional.ofNullable(stored.get()));
		when(repository.saveAndFlush(any(EmailVerification.class))).thenAnswer(invocation -> {
			EmailVerification value = invocation.getArgument(0);
			ReflectionTestUtils.invokeMethod(value, "onCreate");
			stored.set(value);
			return value;
		});
		doAnswer(invocation -> {
			mail.set(invocation.getArgument(0));
			return null;
		}).when(mailSender).send(any(SimpleMailMessage.class));
	}

	@Test
	void publicSendThenVerifyStoresOnlyHashesAndRejectsRepeatedVerification() throws Exception {
		send();
		String code = code();
		assertTrue(code.matches("[0-9]{6}"));
		assertTrue(stored.get().getVerificationCode().startsWith("$2"));
		assertTrue(passwordEncoder.matches(code, stored.get().getVerificationCode()));
		assertTrue(stored.get().getExpiresAt().isAfter(LocalDateTime.now().plusSeconds(290)));
		assertArrayEquals(new String[] {"student@hknu.ac.kr"}, mail.get().getTo());
		var result = mvc.perform(post("/api/auth/email-verifications/verify")
				.contentType(MediaType.APPLICATION_JSON).content(verifyBody(code)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.verified").value(true)).andReturn();
		String token = objectMapper.readTree(result.getResponse().getContentAsString())
				.path("data").path("verificationToken").asText();
		assertFalse(token.isBlank());
		assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
				.digest(token.getBytes(StandardCharsets.UTF_8))), stored.get().getVerificationTokenHash());
		mvc.perform(post("/api/auth/email-verifications/verify").contentType(MediaType.APPLICATION_JSON)
				.content(verifyBody(code))).andExpect(status().isBadRequest());
	}

	@ParameterizedTest
	@ValueSource(strings = {"UTC", "Asia/Seoul", "America/Los_Angeles"})
	void resendIsBlockedUntilIntervalPassesRegardlessOfJvmTimeZone(String timeZone) throws Exception {
		TimeZone original = TimeZone.getDefault();
		try {
			TimeZone.setDefault(TimeZone.getTimeZone(timeZone));
			LocalDateTime before = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
			send();
			assertFalse(stored.get().getCreatedAt().isBefore(before));
			assertFalse(stored.get().getCreatedAt().isAfter(LocalDateTime.now(ZoneId.of("Asia/Seoul"))));
			mvc.perform(post("/api/auth/email-verifications/send").contentType(MediaType.APPLICATION_JSON)
					.content(sendBody("SIGNUP"))).andExpect(status().isBadRequest());
			verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
			ReflectionTestUtils.setField(stored.get(), "createdAt",
					LocalDateTime.now(ZoneId.of("Asia/Seoul")).minusSeconds(61));
			send();
			verify(mailSender, times(2)).send(any(SimpleMailMessage.class));
		} finally {
			TimeZone.setDefault(original);
		}
	}

	@Test
	void wrongAndExpiredCodesDoNotIssueTokens() throws Exception {
		send();
		String code = code();
		String wrong = code.equals("000000") ? "999999" : "000000";
		mvc.perform(post("/api/auth/email-verifications/verify").contentType(MediaType.APPLICATION_JSON)
				.content(verifyBody(wrong))).andExpect(status().isBadRequest());
		ReflectionTestUtils.setField(stored.get(), "expiresAt", LocalDateTime.now().minusSeconds(1));
		mvc.perform(post("/api/auth/email-verifications/verify").contentType(MediaType.APPLICATION_JSON)
				.content(verifyBody(code))).andExpect(status().isBadRequest());
		assertNull(stored.get().getVerificationTokenHash());
	}

	@Test
	void resetPasswordIsNotAnEmailVerificationPurpose() throws Exception {
		mvc.perform(post("/api/auth/email-verifications/send").contentType(MediaType.APPLICATION_JSON)
				.content(sendBody("RESET_PASSWORD"))).andExpect(status().isBadRequest());
		verifyNoInteractions(repository, mailSender);
	}

	@Test
	void smtpFailureDoesNotReturnSuccess() throws Exception {
		doThrow(new MailSendException("SMTP unavailable")).when(mailSender).send(any(SimpleMailMessage.class));
		mvc.perform(post("/api/auth/email-verifications/send").contentType(MediaType.APPLICATION_JSON)
				.content(sendBody("SIGNUP"))).andExpect(status().isInternalServerError());
	}

	private void send() throws Exception {
		mvc.perform(post("/api/auth/email-verifications/send").contentType(MediaType.APPLICATION_JSON)
				.content(sendBody("SIGNUP"))).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.expiresInSeconds").value(300))
				.andExpect(jsonPath("$.data.code").doesNotExist());
	}

	private String code() {
		return mail.get().getText().split("인증번호: ")[1].substring(0, 6);
	}

	private String sendBody(String purpose) {
		return "{\"email\":\"student@hknu.ac.kr\",\"purpose\":\"" + purpose + "\"}";
	}

	private String verifyBody(String code) {
		return "{\"email\":\"student@hknu.ac.kr\",\"purpose\":\"SIGNUP\",\"code\":\"" + code + "\"}";
	}
}
