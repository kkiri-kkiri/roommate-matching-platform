package com.kkirikkiri.user.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import com.kkirikkiri.user.service.UserService;
import com.kkirikkiri.global.config.SecurityConfig;
import com.kkirikkiri.global.security.*;
import java.util.List;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class UserControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean UserService service;
    @MockitoBean JwtAuthenticationProvider authentication;
    @BeforeEach void authenticatedUser() {
        when(authentication.authenticate("test-token")).thenReturn(UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedUser(5L,"USER"),null,List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }
    @Test void requiresAuthenticationAndUsesOnlyPrincipalId() throws Exception {
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/users/me").header("Authorization","Bearer test-token")
                .contentType("application/json").content("{\"nickname\":\"학생둘\"}"))
                .andExpect(status().isOk());
        verify(service).updateMyInfo(eq(5L),argThat(request->"학생둘".equals(request.nickname())));
    }
    @Test void rejectsLongPasswordAndMismatchedConfirmationBeforeCallingService() throws Exception {
        String tooLong="Aa1!"+"a".repeat(69);
        for(String[] values:new String[][]{{tooLong,tooLong},{"New1!pass","Other1!pass"}}) {
            mvc.perform(patch("/api/users/me/password").header("Authorization","Bearer test-token")
                    .contentType("application/json").content("{\"currentPassword\":\"Old1!pass\",\"newPassword\":\""+values[0]+"\",\"newPasswordConfirm\":\""+values[1]+"\"}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        }
        verifyNoInteractions(service);
    }
}
