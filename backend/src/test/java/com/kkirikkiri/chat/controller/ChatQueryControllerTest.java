package com.kkirikkiri.chat.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.kkirikkiri.chat.dto.*;
import com.kkirikkiri.chat.service.ChatService;
import com.kkirikkiri.global.config.SecurityConfig;
import com.kkirikkiri.global.security.*;
import com.kkirikkiri.user.entity.*;
import com.kkirikkiri.user.repository.UserRepository;
import com.kkirikkiri.matching.dto.RoommatePageResponse;

@WebMvcTest(ChatController.class)
@Import({SecurityConfig.class, JwtAuthenticationProvider.class, JwtTokenProvider.class})
class ChatQueryControllerTest {
    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokens;
    @MockitoBean ChatService chat;
    @MockitoBean UserRepository users;
    private String token;

    @BeforeEach void authenticate() {
        when(users.findById(7L)).thenReturn(Optional.of(new User("test-hash", "query@test.invalid")));
        token = tokens.createAccessToken(7L, UserRole.USER);
    }

    @ParameterizedTest
    @ValueSource(strings = {"size=0", "size=-1", "size=101", "size=2147483647", "size=2147483648",
            "page=-1", "page=2147483648", "page=1073741824&size=2", "page=2147483647"})
    void rejectsInvalidPagingOnBothEndpoints(String query) throws Exception {
        for (String endpoint : List.of("/api/chat/rooms", "/api/chat/rooms/10/messages")) {
            mvc.perform(get(endpoint + "?" + query).header("Authorization", "Bearer " + token))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false));
        }
        verifyNoInteractions(chat);
    }

    @ParameterizedTest
    @ValueSource(ints = {50, 100})
    void keepsValidMessagePageEnvelopeAndPrincipal(int size) throws Exception {
        var request = new ChatMessageListRequest(0, size, null);
        when(chat.getMessages(7L, 10L, request)).thenReturn(new RoommatePageResponse<>(List.of(), 0, size, 0, 0, true, true));
        mvc.perform(get("/api/chat/rooms/10/messages?page=0&size=" + size).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.size").value(size));
        verify(chat).getMessages(7L, 10L, request);
    }

    @Test void listsPageMetadataRatherThanSilentlyTruncatingAnArray() throws Exception {
        var request = new ChatRoomListRequest("COMMUNITY", "익명", 1, 50);
        when(chat.getRooms(7L, request)).thenReturn(new ChatRoomPageResponse(List.of(), 1, 50, 120, 3,
                false, false, new ChatRoomCountsResponse(200, 80, 120, 2)));
        mvc.perform(get("/api/chat/rooms?category=COMMUNITY&q=익명&page=1&size=50").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.totalElements").value(120)).andExpect(jsonPath("$.data.last").value(false))
                .andExpect(jsonPath("$.data.counts.all").value(200));
        verify(chat).getRooms(7L, request);
    }

    @Test void requiresAuthenticationForListAndDetail() throws Exception {
        mvc.perform(get("/api/chat/rooms")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/chat/rooms/10")).andExpect(status().isUnauthorized());
        verifyNoInteractions(chat);
    }

    @ParameterizedTest
    @ValueSource(strings = {"beforeId=0", "afterId=-1", "beforeId=1&afterId=2", "beforeId=1&page=1",
            "afterId=9223372036854775808"})
    void rejectsInvalidCursorsBeforeCallingService(String query) throws Exception {
        mvc.perform(get("/api/chat/rooms/10/messages?" + query).header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(chat);
    }

    @Test void readStatusIsAuthenticatedBoundedAndReturnsOnlyReceiptFields() throws Exception {
        var request = new ChatReadRequest(List.of(12L));
        when(chat.getReadStatuses(7L, 10L, request)).thenReturn(List.of(new ChatMessageReadStatusResponse(12L, true)));
        mvc.perform(post("/api/chat/rooms/10/messages/read-status").header("Authorization", "Bearer " + token)
                .contentType("application/json").content("{\"messageIds\":[12]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id").value(12))
                .andExpect(jsonPath("$.data[0].read").value(true)).andExpect(jsonPath("$.data[0].senderId").doesNotExist());
        verify(chat).getReadStatuses(7L, 10L, request);
        mvc.perform(post("/api/chat/rooms/10/messages/read-status").contentType("application/json")
                .content("{\"messageIds\":[12]}" )).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/chat/rooms/10/messages/read-status").header("Authorization", "Bearer " + token)
                .contentType("application/json").content("{\"messageIds\":[]}" )).andExpect(status().isBadRequest());
        mvc.perform(post("/api/chat/rooms/10/messages/read-status").header("Authorization", "Bearer " + token)
                .contentType("application/json").content("{\"messageIds\":[" + "12,".repeat(100) + "12]}"))
                .andExpect(status().isBadRequest());
        verifyNoMoreInteractions(chat);
    }
}
