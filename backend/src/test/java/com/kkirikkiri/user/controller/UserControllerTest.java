package com.kkirikkiri.user.controller;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import com.kkirikkiri.auth.controller.AuthController;
import com.kkirikkiri.auth.service.AccessTokenStore;
import com.kkirikkiri.auth.service.AuthService;
import com.kkirikkiri.global.config.SecurityConfig;
import com.kkirikkiri.user.dto.PasswordChangeRequest;
import com.kkirikkiri.user.dto.UserResponse;
import com.kkirikkiri.user.dto.UserUpdateRequest;
import com.kkirikkiri.user.entity.User;
import com.kkirikkiri.user.entity.UserProfile;
import com.kkirikkiri.user.entity.UserRole;
import com.kkirikkiri.user.entity.UserStatus;
import com.kkirikkiri.user.repository.UserProfileRepository;
import com.kkirikkiri.user.repository.UserRepository;
import com.kkirikkiri.user.service.UserService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({UserController.class, AuthController.class})
@Import({SecurityConfig.class, AuthService.class, AccessTokenStore.class})
class UserControllerTest {

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper objectMapper;
	@MockitoBean UserService userService;
	@MockitoBean UserRepository userRepository;
	@MockitoBean UserProfileRepository profileRepository;

	private User user;

	@BeforeEach
	void setUp() {
		user = mock(User.class);
		when(user.getUserId()).thenReturn(7L);
		when(user.getLoginId()).thenReturn("roomie24");
		when(user.getRole()).thenReturn(UserRole.USER);
		when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
		when(user.getPassword()).thenReturn(new BCryptPasswordEncoder().encode("password123!"));
		when(userRepository.findByLoginId("roomie24")).thenReturn(Optional.of(user));
		when(userRepository.findById(7L)).thenReturn(Optional.of(user));
		UserProfile profile = mock(UserProfile.class);
		when(profile.getNickname()).thenReturn("roomie");
		when(profileRepository.findByUser_UserId(7L)).thenReturn(Optional.of(profile));
	}

	@Test
	void loginTokenConnectsAllThreeEndpointsToAuthenticatedUser() throws Exception {
		String token = login();
		UserResponse response = new UserResponse(7L, "roomie24", "roomie", "r@hknu.ac.kr",
				"여자", "나래", "학부", null, (short) 2002, "24", "3학년", "I", "N", "F", "J");
		when(userService.getMyInfo(7L)).thenReturn(response);
		when(userService.updateMyInfo(eq(7L), any())).thenReturn(response);
		mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token)
				.param("userId", "99"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(7))
				.andExpect(jsonPath("$.success").value(true));
		mvc.perform(patch("/api/users/me").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"roomie\"}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.nickname").value("roomie"));
		mvc.perform(patch("/api/users/me/password").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("""
				{"currentPassword":"password123!","newPassword":"newPassword123!",
				 "newPasswordConfirm":"newPassword123!"}
				"""))
				.andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
				.andExpect(content().json("{\"data\":null}"));
		verify(userService).getMyInfo(7L);
		verify(userService).updateMyInfo(7L,
				new UserUpdateRequest("roomie", null, null, null, null, null, null, null, null));
		verify(userService).changePassword(7L,
				new PasswordChangeRequest("password123!", "newPassword123!", "newPassword123!"));
	}

	@Test
	void rejectsMissingForgedAndMalformedTokensBeforeCallingService() throws Exception {
		mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
		for (String header : new String[]{"Bearer 7", "Basic abc", "Bearer "}) {
			mvc.perform(patch("/api/users/me").header("Authorization", header)
					.contentType(MediaType.APPLICATION_JSON).content("{}"))
					.andExpect(status().isUnauthorized());
		}
		mvc.perform(patch("/api/users/me/password").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isUnauthorized());
		verifyNoInteractions(userService);
	}

	@Test
	void validatesPartialUpdateAndPasswordBeforeCallingService() throws Exception {
		String token = login();
		mvc.perform(patch("/api/users/me").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"grade\":\"9학년\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
		for (String body : new String[]{
				"{}",
				"{\"currentPassword\":\"old\",\"newPassword\":\"short\",\"newPasswordConfirm\":\"short\"}",
				"{\"currentPassword\":\"old\",\"newPassword\":\"password123\",\"newPasswordConfirm\":\"different\"}"}) {
			mvc.perform(patch("/api/users/me/password").header("Authorization", "Bearer " + token)
					.contentType(MediaType.APPLICATION_JSON).content(body))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
		}
		verifyNoInteractions(userService);
	}

	@Test
	void rejectsTokenAfterPasswordChangeOrAccountSuspension() throws Exception {
		String token = login();
		String original = user.getPassword();
		when(user.getPassword()).thenReturn("changed-password-hash");
		mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
				.andExpect(status().isUnauthorized());
		when(user.getPassword()).thenReturn(original);
		when(user.getStatus()).thenReturn(UserStatus.SUSPENDED);
		mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
				.andExpect(status().isUnauthorized());
		verifyNoInteractions(userService);
	}

	@Test
	void invalidLoginCredentialsHaveSameFailureResponse() throws Exception {
		for (String loginId : new String[]{"roomie24", "unknown"}) {
			mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
					.content("{\"loginId\":\"" + loginId + "\",\"password\":\"wrong\"}"))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
		}
		mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest());
	}

	private String login() throws Exception {
		String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"loginId\":\"roomie24\",\"password\":\"password123!\"}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.user.id").value(7))
				.andExpect(jsonPath("$.data.user.role").value("USER"))
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(body).path("data").path("accessToken").asText();
	}
}
