package com.kkirikkiri.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.Optional;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.kkirikkiri.auth.dto.FindIdRequest;
import com.kkirikkiri.auth.dto.PasswordResetConfirmRequest;
import com.kkirikkiri.auth.dto.PasswordResetRequest;
import com.kkirikkiri.auth.entity.EmailVerificationPurpose;
import com.kkirikkiri.auth.entity.PasswordResetToken;
import com.kkirikkiri.auth.repository.PasswordResetTokenRepository;
import com.kkirikkiri.global.exception.CustomException;
import com.kkirikkiri.global.exception.ErrorCode;
import com.kkirikkiri.user.entity.User;
import com.kkirikkiri.user.repository.UserRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AccountRecoveryServiceTest {

	private final UserRepository users = mock(UserRepository.class);
	private final PasswordResetTokenRepository tokens = mock(PasswordResetTokenRepository.class);
	private final PasswordResetMailSender mail = mock(PasswordResetMailSender.class);
	private final EmailVerificationService verification = mock(EmailVerificationService.class);
	private final PasswordEncoder encoder = new BCryptPasswordEncoder();
	private final Clock clock = Clock.fixed(Instant.parse("2026-09-21T00:00:00Z"), ZoneId.of("Asia/Seoul"));
	private ValidatorFactory validators;
	private PasswordResetService reset;
	private FindIdService findId;
	private User user;

	@BeforeEach
	void setUp() {
		validators = Validation.buildDefaultValidatorFactory();
		reset = new PasswordResetService(users, tokens, mail, encoder, validators.getValidator(), 300, clock);
		findId = new FindIdService(users, verification, validators.getValidator());
		user = new User("student", encoder.encode("old-password"), "student@hknu.ac.kr");
	}

	@AfterEach
	void close() {
		validators.close();
	}

	@Test
	void requestSendsRawTokenButStoresHashAndExpiry() throws Exception {
		when(users.findByLoginId("student")).thenReturn(Optional.of(user));
		assertTrue(reset.requestPasswordReset(new PasswordResetRequest("student", user.getSchoolEmail())).sent());
		var saved = ArgumentCaptor.forClass(PasswordResetToken.class);
		verify(tokens).saveAndFlush(saved.capture());
		var raw = ArgumentCaptor.forClass(String.class);
		verify(mail).send(eq(user.getSchoolEmail()), raw.capture(), eq(300L));
		assertEquals(43, raw.getValue().length());
		assertEquals(hash(raw.getValue()), saved.getValue().getTokenHash());
		assertNotEquals(raw.getValue(), saved.getValue().getTokenHash());
		assertEquals(LocalDateTime.now(clock).plusSeconds(300), saved.getValue().getExpiresAt());
		assertSame(user, saved.getValue().getUser());
	}

	@Test
	void missingAccountAndMismatchedEmailReturnSameResponseWithoutSending() {
		when(users.findByLoginId("missing")).thenReturn(Optional.empty());
		when(users.findByLoginId("student")).thenReturn(Optional.of(user));
		assertEquals(reset.requestPasswordReset(new PasswordResetRequest("missing", "other@hknu.ac.kr")),
				reset.requestPasswordReset(new PasswordResetRequest("student", "other@hknu.ac.kr")));
		verifyNoInteractions(tokens, mail);
	}

	@Test
	void resetChangesPasswordAndConsumesTokenOnce() throws Exception {
		PasswordResetToken token = token(LocalDateTime.now(clock).plusSeconds(300));
		when(tokens.findByTokenHash(hash("raw-token"))).thenReturn(Optional.of(token));
		reset.resetPassword(confirm("new-password", "new-password"));
		assertTrue(encoder.matches("new-password", user.getPassword()));
		assertTrue(token.isUsed());
		assertThrows(CustomException.class, () -> reset.resetPassword(confirm("another-password", "another-password")));
		assertTrue(encoder.matches("new-password", user.getPassword()));
	}

	@Test
	void expiredTokenAtExactBoundaryCannotChangePassword() throws Exception {
		PasswordResetToken token = token(LocalDateTime.now(clock));
		when(tokens.findByTokenHash(hash("raw-token"))).thenReturn(Optional.of(token));
		assertThrows(CustomException.class, () -> reset.resetPassword(confirm("new-password", "new-password")));
		assertTrue(encoder.matches("old-password", user.getPassword()));
		assertFalse(token.isUsed());
	}

	@Test
	void missingTokenAndInvalidPasswordsAreRejected() {
		assertThrows(CustomException.class, () -> reset.resetPassword(confirm("new-password", "new-password")));
		clearInvocations(tokens);
		assertThrows(CustomException.class, () -> reset.resetPassword(confirm("short", "short")));
		assertThrows(CustomException.class, () -> reset.resetPassword(confirm("new-password", "mismatch")));
		verifyNoInteractions(tokens);
	}

	@Test
	void mailFailurePropagatesToTransactionalCaller() {
		when(users.findByLoginId("student")).thenReturn(Optional.of(user));
		doThrow(new CustomException(ErrorCode.INTERNAL_SERVER_ERROR)).when(mail).send(anyString(), anyString(), anyLong());
		assertThrows(CustomException.class,
				() -> reset.requestPasswordReset(new PasswordResetRequest("student", user.getSchoolEmail())));
	}

	@Test
	void findIdConsumesMatchingPurposeBeforeReturningLoginId() {
		when(users.findBySchoolEmail(user.getSchoolEmail())).thenReturn(Optional.of(user));
		assertEquals("student", findId.findId(new FindIdRequest(user.getSchoolEmail(), "proof")).loginId());
		var order = inOrder(verification, users);
		order.verify(verification).consumeToken(user.getSchoolEmail(), EmailVerificationPurpose.FIND_ID, "proof");
		order.verify(users).findBySchoolEmail(user.getSchoolEmail());
	}

	@Test
	void findIdRejectsMissingAndInvalidProof() {
		assertThrows(CustomException.class, () -> findId.findId(new FindIdRequest(user.getSchoolEmail(), " ")));
		verifyNoInteractions(verification, users);
		doThrow(new CustomException(ErrorCode.INVALID_REQUEST)).when(verification)
				.consumeToken(user.getSchoolEmail(), EmailVerificationPurpose.FIND_ID, "invalid");
		assertThrows(CustomException.class, () -> findId.findId(new FindIdRequest(user.getSchoolEmail(), "invalid")));
		verifyNoInteractions(users);
	}

	private PasswordResetToken token(LocalDateTime expiresAt) throws Exception {
		return new PasswordResetToken(user, hash("raw-token"), expiresAt);
	}

	private PasswordResetConfirmRequest confirm(String password, String confirmation) {
		return new PasswordResetConfirmRequest("raw-token", password, confirmation);
	}

	private String hash(String value) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
	}
}
