package com.kkirikkiri.chat.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.kkirikkiri.chat.dto.ChatReadRequest;
import com.kkirikkiri.chat.service.ChatService;
import com.kkirikkiri.global.config.SecurityConfig;
import com.kkirikkiri.global.exception.*;
import com.kkirikkiri.global.security.*;
import com.kkirikkiri.user.entity.*;
import com.kkirikkiri.user.repository.UserRepository;

@WebMvcTest(ChatController.class)
@Import({SecurityConfig.class, JwtAuthenticationProvider.class, JwtTokenProvider.class})
class ChatReadControllerTest {
    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokens;
    @MockitoBean ChatService chat;
    @MockitoBean UserRepository users;
    private String token;

    @BeforeEach
    void authenticate() {
        var user = new User("test-hash", "test@audit.invalid");
        when(users.findById(7L)).thenReturn(Optional.of(user));
        token = tokens.createAccessToken(7L, UserRole.USER);
    }

    @Test
    void keepsEnvelopeAndUsesPrincipalRatherThanAnInjectedUserId() throws Exception {
        mvc.perform(post("/api/chat/rooms/10/read").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"messageIds\":[42,43],\"userId\":999}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isEmpty());
        verify(chat).markRead(7L, 10L, new ChatReadRequest(List.of(42L, 43L)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"messageIds\":null}", "{\"messageIds\":[]}", "{\"messageIds\":[null]}",
            "{\"messageIds\":[0]}", "{\"messageIds\":[-1]}", "{\"messageIds\":[\"invalid\"]}"})
    void rejectsInvalidReceiptsBeforeCallingService(String body) throws Exception {
        mvc.perform(post("/api/chat/rooms/10/read").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false));
        verifyNoInteractions(chat);
    }

    @Test
    void rejectsMissingOrOversizedBody() throws Exception {
        mvc.perform(post("/api/chat/rooms/10/read").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/chat/rooms/10/read").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"messageIds\":[" + String.join(",", java.util.Collections.nCopies(101, "42")) + "]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        verifyNoInteractions(chat);
    }

    @Test
    void preservesForbiddenResponseAndRejectsUnauthenticatedCaller() throws Exception {
        doThrow(new CustomException(ErrorCode.FORBIDDEN)).when(chat).markRead(anyLong(), anyLong(), any());
        mvc.perform(post("/api/chat/rooms/10/read").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"messageIds\":[42]}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
        clearInvocations(chat);
        mvc.perform(post("/api/chat/rooms/10/read").contentType(MediaType.APPLICATION_JSON).content("{\"messageIds\":[42]}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(chat);
    }
}
