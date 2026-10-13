package com.kkirikkiri.auth.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;

import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import com.kkirikkiri.auth.service.AuthService;
import com.kkirikkiri.global.config.SecurityConfig;
import com.kkirikkiri.global.security.JwtAuthenticationProvider;
import com.kkirikkiri.global.security.JwtTokenProvider;
import com.kkirikkiri.user.entity.User;
import com.kkirikkiri.user.entity.UserProfile;
import com.kkirikkiri.user.entity.UserRole;
import com.kkirikkiri.user.entity.UserStatus;
import com.kkirikkiri.user.repository.UserProfileRepository;
import com.kkirikkiri.user.repository.UserRepository;

@WebMvcTest(controllers = AuthController.class, properties = {
        "jwt.secret=" + AuthSessionTest.SECRET,
        "jwt.access-token-expiration-seconds=3600"
})
@Import({ SecurityConfig.class, JwtAuthenticationProvider.class, JwtTokenProvider.class, AuthService.class })
class AuthSessionTest {
    static final String SECRET = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokens;
    @MockitoBean UserRepository users;
    @MockitoBean UserProfileRepository profiles;
    @MockitoBean Validator validator;
    private User user;

    @BeforeEach
    void setUp() {
        user = mock(User.class);
        when(user.getUserId()).thenReturn(1L);
        when(user.getSchoolEmail()).thenReturn("student@hknu.ac.kr");
        when(user.getRole()).thenReturn(UserRole.USER);
        when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
        when(users.findById(1L)).thenReturn(Optional.of(user));
        UserProfile profile = mock(UserProfile.class);
        when(profile.getNickname()).thenReturn("학생");
        when(profiles.findByUser_UserId(1L)).thenReturn(Optional.of(profile));
    }

    @Test
    void returnsVerifiedCurrentAccount() throws Exception {
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + tokens.createAccessToken(1L, UserRole.USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.nickname").value("학생"))
                .andExpect(jsonPath("$.data.schoolEmail").value("student@hknu.ac.kr"))
                .andExpect(jsonPath("$.data.role").value("USER"));
    }

    @Test
    void rejectsMissingExpiredAndBadSignatureTokens() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + signedToken(-1, SECRET)))
                .andExpect(status().isUnauthorized());
        String differentKey = Base64.getEncoder().encodeToString(new byte[32]);
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + signedToken(3600, differentKey)))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(profiles);
    }

    @Test
    void rejectsDeletedAndInactiveAccounts() throws Exception {
        String token = tokens.createAccessToken(1L, UserRole.USER);
        when(users.findById(1L)).thenReturn(Optional.empty());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        when(users.findById(1L)).thenReturn(Optional.of(user));
        when(user.getStatus()).thenReturn(UserStatus.WITHDRAWN);
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(profiles);
    }

    @Test
    void verifiesAdminWithoutAProfileAndReturnsCurrentDatabaseRole() throws Exception {
        when(user.getRole()).thenReturn(UserRole.ADMIN);
        when(profiles.findByUser_UserId(1L)).thenReturn(Optional.empty());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + tokens.createAccessToken(1L, UserRole.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("ADMIN"))
                .andExpect(jsonPath("$.data.nickname").value("student@hknu.ac.kr"));
    }

    private String signedToken(long expiresIn, String secret) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), new JWTClaimsSet.Builder()
                .claim("userId", 1L).claim("role", "USER")
                .expirationTime(Date.from(Instant.now().plusSeconds(expiresIn))).build());
        jwt.sign(new MACSigner(Base64.getDecoder().decode(secret)));
        return jwt.serialize();
    }
}
