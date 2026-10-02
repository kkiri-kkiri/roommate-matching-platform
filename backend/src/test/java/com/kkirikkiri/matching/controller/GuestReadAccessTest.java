package com.kkirikkiri.matching.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.kkirikkiri.community.post.controller.CommunityPostController;
import com.kkirikkiri.community.post.dto.CommunityPostListRequest;
import com.kkirikkiri.community.post.service.CommunityPostService;
import com.kkirikkiri.global.config.SecurityConfig;
import com.kkirikkiri.global.security.JwtAuthenticationProvider;
import com.kkirikkiri.matching.dto.RoommateFilterRequest;
import com.kkirikkiri.matching.service.RoommateMatchingService;

@WebMvcTest({RoommateMatchingController.class, CommunityPostController.class})
@Import(SecurityConfig.class)
class GuestReadAccessTest {
    @Autowired MockMvc mvc;
    @MockitoBean RoommateMatchingService roommates;
    @MockitoBean CommunityPostService posts;
    @MockitoBean JwtAuthenticationProvider jwtAuthenticationProvider;

    @Test
    void guestsCanReadProfilesAndCommunityWithNoPrincipal() throws Exception {
        mvc.perform(get("/api/roommates")).andExpect(status().isOk());
        verify(roommates).getRoommates(isNull(), any(RoommateFilterRequest.class), any(Pageable.class));
        mvc.perform(get("/api/roommates/42")).andExpect(status().isOk());
        verify(roommates).getRoommateDetail(null, 42L);
        mvc.perform(get("/api/posts")).andExpect(status().isOk());
        verify(posts).getPosts(isNull(), any(CommunityPostListRequest.class));
        mvc.perform(get("/api/posts/12")).andExpect(status().isOk());
        verify(posts).getPost(null, 12L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/roommates/recommended", "/api/users/me/liked-roommates", "/api/users/me/posts", "/api/users/me/comments"})
    void guestsCannotReadPersonalizedData(String url) throws Exception {
        mvc.perform(get(url)).andExpect(status().isUnauthorized());
        verifyNoInteractions(roommates, posts);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/roommates/42/likes", "/api/roommates/42/ai-analysis", "/api/posts", "/api/posts/12/likes", "/api/posts/12/comments"})
    void guestWritesStillRequireAuthentication(String url) throws Exception {
        mvc.perform(post(url)).andExpect(status().isUnauthorized());
        verifyNoInteractions(roommates, posts);
    }
}
