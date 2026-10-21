package com.kkirikkiri.integration.auth;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.kkirikkiri.auth.dto.SignupRequest;
import com.kkirikkiri.integration.support.ApiIntegrationTest;
import com.kkirikkiri.integration.support.TestUsers;
import com.kkirikkiri.user.entity.User;
import com.kkirikkiri.user.entity.UserStatus;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Auth reservations and mail issuance commit independently; fixtures and cleanup do too. */
@ApiIntegrationTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
// Keep independently committed global mail quota separate from other API test contexts.
@TestPropertySource(properties = "auth.email-verification.mail-from=auth-api@example.invalid")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired TestUsers users;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired JavaMailSender smtp;
    @Autowired PasswordEncoder encoder;
    @Autowired PlatformTransactionManager manager;
    TransactionTemplate tx;
    User user;
    Long dormId, facultyId;
    String dormitory, faculty, signupEmail, wrongEmail, nickname;
    Set<String> originalBuckets;
    Set<Long> originalIdentities;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(manager);
        originalBuckets = new HashSet<>(jdbc.queryForList("select bucket_key from AUTH_LIMIT_BUCKET", String.class));
        originalIdentities = new HashSet<>(jdbc.queryForList("select identity_id from AUTH_EMAIL_IDENTITY", Long.class));
        signupEmail = UUID.randomUUID() + "@hknu.ac.kr";
        wrongEmail = UUID.randomUUID() + "@hknu.ac.kr";
        nickname = UUID.randomUUID().toString().substring(0, 8);
        tx.executeWithoutResult(s -> {
            user = users.createUser();
            var profile = users.createProfile(user);
            dormId = profile.getDormitory().getDormitoryId();
            facultyId = profile.getFaculty().getFacultyId();
            dormitory = profile.getDormitory().getDormitoryName();
            faculty = profile.getFaculty().getFacultyName();
        });
    }

    @AfterEach
    void cleanUpCommittedRows() {
        tx.executeWithoutResult(s -> {
            for (String email : Set.of(user.getSchoolEmail(), signupEmail, wrongEmail)) {
                jdbc.update("delete from PASSWORD_RESET_TOKEN where user_id in (select user_id from USER where school_email=?)", email);
                jdbc.update("delete from EMAIL_VERIFICATION where email=?", email);
                jdbc.update("delete from USER_PROFILE where user_id in (select user_id from USER where school_email=?)", email);
                jdbc.update("delete from USER where school_email=?", email);
            }
            jdbc.update("delete from DORMITORY where dormitory_id=?", dormId);
            jdbc.update("delete from FACULTY where faculty_id=?", facultyId);
            // JUnit is sequential. Remove only independently committed rows introduced by this test.
            for (String key : jdbc.queryForList("select bucket_key from AUTH_LIMIT_BUCKET", String.class)) {
                if (!originalBuckets.contains(key)) jdbc.update("delete from AUTH_LIMIT_BUCKET where bucket_key=?", key);
            }
            for (Long id : jdbc.queryForList("select identity_id from AUTH_EMAIL_IDENTITY", Long.class)) {
                if (!originalIdentities.contains(id)) jdbc.update("delete from AUTH_EMAIL_IDENTITY where identity_id=?", id);
            }
        });
        assertEquals(originalBuckets, new HashSet<>(jdbc.queryForList("select bucket_key from AUTH_LIMIT_BUCKET", String.class)));
        assertEquals(originalIdentities, new HashSet<>(jdbc.queryForList("select identity_id from AUTH_EMAIL_IDENTITY", Long.class)));
        assertEquals(0, jdbc.queryForObject("select count(*) from USER where school_email in (?,?,?)", Integer.class,
                user.getSchoolEmail(), signupEmail, wrongEmail));
    }

    @Test
    void emailVerificationSignupAndLoginProduceUsableJwtAndPersistProfile() throws Exception {
        call("/login/email-check", Map.of("email", signupEmail)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.registered").value(false));
        call("/email-verifications/send", Map.of("email", signupEmail, "purpose", "SIGNUP"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.expiresInSeconds").value(300));
        String code = mailValue("인증번호: ");
        var verified = data(call("/email-verifications/verify", Map.of("email", signupEmail, "purpose", "SIGNUP", "code", code))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.verified").value(true)));
        String token = verified.path("verificationToken").asString();
        assertFalse(token.isBlank());
        var stored = jdbc.queryForMap("select * from EMAIL_VERIFICATION where email=?", signupEmail);
        assertTrue(encoder.matches(code, (String) stored.get("VERIFICATION_CODE")));
        assertNotEquals(token, stored.get("VERIFICATION_TOKEN_HASH"));

        // A token from another email must not create an account or consume the legitimate token.
        call("/signup", signup(wrongEmail, token))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("EMAIL_VERIFICATION_TOKEN_INVALID"));
        assertNull(jdbc.queryForObject("select consumed_at from EMAIL_VERIFICATION where email=?", Object.class, signupEmail));
        assertEquals(0, jdbc.queryForObject("select count(*) from USER where school_email=?", Integer.class, signupEmail));
        assertEquals(0, jdbc.queryForObject("select count(*) from USER where school_email=?", Integer.class, wrongEmail));
        var result = data(call("/signup", signup(signupEmail, token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value(nickname)));
        long id = result.path("userId").asLong();
        var account = jdbc.queryForMap("select * from USER where user_id=?", id);
        assertTrue((Boolean) account.get("EMAIL_VERIFIED"));
        assertTrue(encoder.matches(TestUsers.PASSWORD, (String) account.get("PASSWORD")));
        var profile = jdbc.queryForMap("select * from USER_PROFILE where user_id=?", id);
        assertEquals("INTJ", profile.get("MBTI"));
        assertNull(profile.get("MAJOR_ID"));
        assertNotNull(jdbc.queryForObject("select consumed_at from EMAIL_VERIFICATION where email=?", Object.class, signupEmail));
        mvc.perform(get("/api/auth/signup/availability").param("schoolEmail", signupEmail).param("nickname", nickname))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.schoolEmailAvailable").value(false))
                .andExpect(jsonPath("$.data.nicknameAvailable").value(false));
        call("/signup", signup(signupEmail, token)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_ACCOUNT_VALUE"));
        call("/login/email-check", Map.of("email", signupEmail)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.registered").value(true));
        String jwt = login(signupEmail, TestUsers.PASSWORD);
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.nickname").value(nickname));
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.faculty").value(faculty));
    }

    @Test
    void expiredCodeCannotIssueVerificationToken() throws Exception {
        call("/email-verifications/send", Map.of("email", signupEmail, "purpose", "SIGNUP"))
                .andExpect(status().isOk());
        String code = mailValue("인증번호: ");
        jdbc.update("update EMAIL_VERIFICATION set expires_at=? where email=?",
                java.time.LocalDateTime.of(2000, 1, 1, 0, 0), signupEmail);
        call("/email-verifications/verify", Map.of("email", signupEmail, "purpose", "SIGNUP", "code", code))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("EMAIL_VERIFICATION_EXPIRED"));
        var row = jdbc.queryForMap("select * from EMAIL_VERIFICATION where email=?", signupEmail);
        assertNull(row.get("VERIFIED_AT"));
        assertNull(row.get("VERIFICATION_TOKEN_HASH"));
        assertNull(row.get("CONSUMED_AT"));
    }

    @Test
    void expiredSignupTokenCannotCreateUserOrProfile() throws Exception {
        call("/email-verifications/send", Map.of("email", signupEmail, "purpose", "SIGNUP"))
                .andExpect(status().isOk());
        String token = data(call("/email-verifications/verify", Map.of("email", signupEmail, "purpose", "SIGNUP",
                        "code", mailValue("인증번호: "))).andExpect(status().isOk()))
                .path("verificationToken").asString();
        jdbc.update("update EMAIL_VERIFICATION set verification_token_expires_at=? where email=?",
                java.time.LocalDateTime.of(2000, 1, 1, 0, 0), signupEmail);
        call("/signup", signup(signupEmail, token)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("EMAIL_VERIFICATION_TOKEN_INVALID"));
        assertEquals(0, jdbc.queryForObject("select count(*) from USER where school_email=?", Integer.class, signupEmail));
        assertEquals(0, jdbc.queryForObject("select count(*) from USER_PROFILE where nickname=?", Integer.class, nickname));
        assertNull(jdbc.queryForObject("select consumed_at from EMAIL_VERIFICATION where email=?", Object.class, signupEmail));
    }

    @Test
    void unknownResetTokenCannotChangePassword() throws Exception {
        String before = password();
        call("/password-reset/confirm", reset("unknown-token", "Changed123!"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("PASSWORD_RESET_TOKEN_INVALID"));
        assertEquals(before, password());
        verifyNoInteractions(smtp);
    }

    @Test
    void resetPasswordCommitsHashAndTokenUseAndOnlyNewPasswordCanLogin() throws Exception {
        String token = requestReset();
        String storedHash = jdbc.queryForObject("select token_hash from PASSWORD_RESET_TOKEN where user_id=?", String.class, user.getUserId());
        assertNotEquals(token, storedHash);
        call("/password-reset/confirm", reset(token, "Changed123!"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));
        String password = password();
        assertTrue(encoder.matches("Changed123!", password));
        assertFalse(encoder.matches(TestUsers.PASSWORD, password));
        assertNotNull(jdbc.queryForObject("select used_at from PASSWORD_RESET_TOKEN where user_id=?", Object.class, user.getUserId()));
        call("/password-reset/confirm", reset(token, "Another123!"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("PASSWORD_RESET_TOKEN_ALREADY_USED"));
        assertEquals(password, password());
        call("/login", Map.of("email", user.getSchoolEmail(), "password", TestUsers.PASSWORD))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + login(user.getSchoolEmail(), "Changed123!")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(user.getUserId()));
    }

    @ParameterizedTest
    @CsvSource({"EXPIRED,PASSWORD_RESET_TOKEN_EXPIRED", "SUSPENDED,PASSWORD_RESET_ACCOUNT_INACTIVE", "WITHDRAWN,PASSWORD_RESET_ACCOUNT_INACTIVE"})
    void resetRechecksExpiryAndAccountStatusWithoutUsingToken(String state, String error) throws Exception {
        String token = requestReset();
        String password = password();
        if (state.equals("EXPIRED")) {
            jdbc.update("update PASSWORD_RESET_TOKEN set expires_at=? where user_id=?",
                    java.time.LocalDateTime.of(2000, 1, 1, 0, 0), user.getUserId());
        } else {
            tx.executeWithoutResult(s -> jdbc.update("update USER set status=? where user_id=?", state, user.getUserId()));
        }
        call("/password-reset/confirm", reset(token, "Changed123!"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value(error));
        assertEquals(password, password());
        assertNull(jdbc.queryForObject("select used_at from PASSWORD_RESET_TOKEN where user_id=?", Object.class, user.getUserId()));
    }

    @ParameterizedTest
    @EnumSource(value = UserStatus.class, names = {"SUSPENDED", "WITHDRAWN"})
    void inactiveAccountRejectsPreviouslyIssuedJwtAndCredentialsAndDoesNotSendReset(UserStatus state) throws Exception {
        String jwt = login(user.getSchoolEmail(), TestUsers.PASSWORD);
        jdbc.update("update USER set status=? where user_id=?", state.name(), user.getUserId());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + jwt))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
        call("/login", Map.of("email", user.getSchoolEmail(), "password", TestUsers.PASSWORD))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
        call("/password-reset/request", Map.of("email", user.getSchoolEmail()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.sent").value(true));
        verifyNoInteractions(smtp);
        assertEquals(0, jdbc.queryForObject("select count(*) from PASSWORD_RESET_TOKEN where user_id=?", Integer.class, user.getUserId()));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void mailFailureRollsBackIssuanceAndHttpRetryRetainsCooldown(boolean reset) throws Exception {
        doThrow(new MailSendException("test-only mail failure")).when(smtp).send(any(SimpleMailMessage.class));
        String path = reset ? "/password-reset/request" : "/email-verifications/send";
        Object request = reset ? Map.of("email", user.getSchoolEmail()) : Map.of("email", signupEmail, "purpose", "SIGNUP");
        var failure = call(path, request).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_SERVER_ERROR"))
                .andExpect(header().exists("X-Request-ID"));
        assertFalse(failure.andReturn().getResponse().getContentAsString().contains("test-only mail failure"));
        assertEquals(0, jdbc.queryForObject("select count(*) from EMAIL_VERIFICATION where email=?", Integer.class, signupEmail));
        assertEquals(0, jdbc.queryForObject("select count(*) from PASSWORD_RESET_TOKEN where user_id=?", Integer.class, user.getUserId()));
        call(path, request).andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", org.hamcrest.Matchers.matchesPattern("[1-9][0-9]*")))
                .andExpect(jsonPath("$.errorCode").value(reset ? "AUTH_REQUEST_LIMITED" : "EMAIL_VERIFICATION_COOLDOWN"));
        verify(smtp, times(1)).send(any(SimpleMailMessage.class));
    }

    @ParameterizedTest
    @CsvSource({"/email-verifications/send,email,outside@example.invalid", "/email-verifications/verify,code,12345", "/password-reset/confirm,newPasswordConfirm,Different123!"})
    void invalidPublicDtoCannotSendMailOrWriteTokens(String path, String field, String value) throws Exception {
        var request = new java.util.LinkedHashMap<String, Object>(Map.of("email", signupEmail, "purpose", "SIGNUP",
                "code", "123456", "resetToken", "unknown", "newPassword", "Changed123!", "newPasswordConfirm", "Changed123!"));
        request.put(field, value);
        call(path, request).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        verifyNoInteractions(smtp);
        assertEquals(originalBuckets, new HashSet<>(jdbc.queryForList("select bucket_key from AUTH_LIMIT_BUCKET", String.class)));
        assertEquals(0, jdbc.queryForObject("select count(*) from EMAIL_VERIFICATION where email=?", Integer.class, signupEmail));
        assertEquals(0, jdbc.queryForObject("select count(*) from PASSWORD_RESET_TOKEN where user_id=?", Integer.class, user.getUserId()));
    }

    private ResultActions call(String path, Object request) throws Exception {
        return mvc.perform(post("/api/auth" + path).contentType("application/json").content(json.writeValueAsString(request)));
    }

    private JsonNode data(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
    }

    private String login(String email, String password) throws Exception {
        return data(call("/login", Map.of("email", email, "password", password))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.user.schoolEmail").value(email)))
                .path("accessToken").asString();
    }

    private String requestReset() throws Exception {
        call("/password-reset/request", Map.of("email", user.getSchoolEmail()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.sent").value(true));
        return mailValue("비밀번호 재설정 토큰: ");
    }

    private String mailValue(String prefix) {
        var message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(smtp).send(message.capture());
        return message.getValue().getText().lines().filter(line -> line.startsWith(prefix))
                .findFirst().orElseThrow().substring(prefix.length());
    }

    private SignupRequest signup(String email, String token) {
        return new SignupRequest(TestUsers.PASSWORD, TestUsers.PASSWORD, nickname, email, token,
                "남자", dormitory, faculty, "세부전공 없음", (short) 2003, "22", "1학년", "I", "N", "T", "J");
    }

    private Map<String, String> reset(String token, String password) {
        return Map.of("resetToken", token, "newPassword", password, "newPasswordConfirm", password);
    }

    private String password() {
        return jdbc.queryForObject("select password from USER where user_id=?", String.class, user.getUserId());
    }
}
