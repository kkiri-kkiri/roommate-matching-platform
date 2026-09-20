package com.kkirikkiri.global.security;

import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import com.kkirikkiri.auth.controller.AuthController;
import com.kkirikkiri.auth.service.AuthService;
import com.kkirikkiri.global.config.SecurityConfig;
import com.kkirikkiri.user.controller.UserController;
import com.kkirikkiri.user.entity.Dormitory;
import com.kkirikkiri.user.entity.Faculty;
import com.kkirikkiri.user.entity.User;
import com.kkirikkiri.user.entity.UserProfile;
import com.kkirikkiri.user.entity.UserRole;
import com.kkirikkiri.user.entity.UserStatus;
import com.kkirikkiri.user.repository.DormitoryRepository;
import com.kkirikkiri.user.repository.FacultyRepository;
import com.kkirikkiri.user.repository.MajorRepository;
import com.kkirikkiri.user.repository.UserProfileRepository;
import com.kkirikkiri.user.repository.UserRepository;
import com.kkirikkiri.user.service.UserService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 실제 MVC/보안/Service를 연결하고 DB Repository만 대체한다. */
@WebMvcTest(controllers = {AuthController.class, UserController.class}, properties = {
		"jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
		"jwt.access-token-expiration-seconds=3600"
})
@Import({SecurityConfig.class, JwtTokenProvider.class, AuthService.class, UserService.class})
class AuthenticationIntegrationTest {

	private static final Long USER_ID = 3_000_000_000L;
	@Autowired MockMvc mvc;
	@Autowired ObjectMapper objectMapper;
	@Autowired PasswordEncoder passwordEncoder;
	@Autowired JwtTokenProvider jwtTokenProvider;
	@MockitoBean UserRepository userRepository;
	@MockitoBean UserProfileRepository userProfileRepository;
	@MockitoBean DormitoryRepository dormitoryRepository;
	@MockitoBean FacultyRepository facultyRepository;
	@MockitoBean MajorRepository majorRepository;
	private User user;

	@BeforeEach
	void setUp() {
		user = new User("test01", passwordEncoder.encode("test1234"), "test@hknu.ac.kr");
		ReflectionTestUtils.setField(user, "userId", USER_ID);
		Dormitory dormitory = mock(Dormitory.class);
		Faculty faculty = mock(Faculty.class);
		when(dormitory.getDormitoryName()).thenReturn("나래");
		when(faculty.getFacultyName()).thenReturn("컴퓨터응용수학부");
		UserProfile profile = new UserProfile(user, "차분한하루", "여자", dormitory, faculty, null,
				(short) 2002, "24", "3학년", "INFJ");
		when(userRepository.findByLoginId("test01")).thenReturn(Optional.of(user));
		when(userProfileRepository.findByUser_UserId(USER_ID)).thenReturn(Optional.of(profile));
	}

	@Test
	void loginThenMeThroughJwtFilterAndAuthenticationPrincipal() throws Exception {
		var login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"loginId\":\"test01\",\"password\":\"test1234\"}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.user.nickname").value("차분한하루"))
				.andReturn();
		String token = objectMapper.readTree(login.getResponse().getContentAsString())
				.path("data").path("accessToken").asText();
		assertFalse(token.isBlank());
		assertEquals(new AuthenticatedUser(USER_ID, "USER"), jwtTokenProvider.getAuthenticatedUser(token));
		clearInvocations(userProfileRepository);
		var me = mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.loginId").value("test01"))
				.andExpect(jsonPath("$.data.nickname").value("차분한하루")).andReturn();
		assertEquals(USER_ID.longValue(), objectMapper.readTree(me.getResponse().getContentAsString())
				.path("data").path("id").asLong());
		// Controller의 @AuthenticationPrincipal에서 실제 UserService로 Long ID가 전달되었는지 확인.
		verify(userProfileRepository).findByUser_UserId(USER_ID);
		assertNull(login.getRequest().getSession(false));
		assertNull(me.getRequest().getSession(false));
		mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
	}

	@Test
	void missingTokenIsUnauthorized() throws Exception {
		mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
		verifyNoInteractions(userProfileRepository);
	}

	@Test
	void forgedTokenIsUnauthorized() throws Exception {
		byte[] wrongKey = new byte[32];
		wrongKey[0] = 1;
		String token = new JwtTokenProvider(Base64.getEncoder().encodeToString(wrongKey), 3600)
				.createAccessToken(USER_ID, UserRole.USER);
		assertRejected(token);
	}

	@Test
	void expiredTokenIsUnauthorized() throws Exception {
		SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), new JWTClaimsSet.Builder()
				.claim("userId", USER_ID).claim("role", "USER")
				.expirationTime(Date.from(Instant.now().minusSeconds(60))).build());
		jwt.sign(new MACSigner(new byte[32]));
		assertRejected(jwt.serialize());
	}

	@Test
	void malformedTokenIsUnauthorized() throws Exception {
		assertRejected("broken.token.value");
	}

	@Test
	void wrongIdAndWrongPasswordHaveSameResponse() throws Exception {
		String missingUser = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"loginId\":\"missing\",\"password\":\"test1234\"}"))
				.andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
		String wrongPassword = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"loginId\":\"test01\",\"password\":\"wrong\"}"))
				.andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
		assertEquals(missingUser, wrongPassword);
	}

	@Test
	void inactiveUserCannotLogin() throws Exception {
		for (UserStatus status : new UserStatus[] {UserStatus.SUSPENDED, UserStatus.WITHDRAWN}) {
			ReflectionTestUtils.setField(user, "status", status);
			mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
					.content("{\"loginId\":\"test01\",\"password\":\"test1234\"}"))
					.andExpect(status().isUnauthorized());
		}
		verifyNoInteractions(userProfileRepository);
	}

	@Test
	void blankLoginIsValidated() throws Exception {
		mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"loginId\":\"\",\"password\":\"\"}"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
		verifyNoInteractions(userRepository);
	}

	private void assertRejected(String token) throws Exception {
		mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
				.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
		verifyNoInteractions(userProfileRepository);
	}
}
