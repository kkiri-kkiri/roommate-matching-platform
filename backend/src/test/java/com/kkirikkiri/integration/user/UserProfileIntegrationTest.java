package com.kkirikkiri.integration.user;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.transaction.AfterTransaction;

import com.kkirikkiri.integration.support.ApiIntegrationTest;
import com.kkirikkiri.integration.support.TestUsers;
import com.kkirikkiri.user.entity.User;
import com.kkirikkiri.user.entity.UserStatus;
import com.kkirikkiri.user.repository.UserProfileRepository;
import com.kkirikkiri.user.repository.UserRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ApiIntegrationTest
class UserProfileIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired TestUsers users;
    @Autowired EntityManager em;
    @Autowired UserProfileRepository profiles;
    @Autowired UserRepository accounts;

    User user;
    String originalNickname;

    @BeforeEach
    void setUp() {
        user = users.createUser();
        originalNickname = users.createProfile(user).getNickname();
        em.clear();
    }

    @Test
    void authenticatedMemberUpdatesNicknameInDatabase() throws Exception {
        mvc.perform(patch("/api/users/me").with(users.authenticated(user))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"nickname":"newname"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(user.getUserId()))
                .andExpect(jsonPath("$.data.nickname").value("newname"))
                .andExpect(jsonPath("$.data.schoolEmail").value(user.getSchoolEmail()));

        em.flush();
        em.clear();
        assertEquals("newname", profiles.findByUser_UserId(user.getUserId()).orElseThrow().getNickname());
    }

    @Test
    void unauthenticatedRequestCannotUpdateProfile() throws Exception {
        mvc.perform(patch("/api/users/me").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"newname\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
        assertNicknameUnchanged();
    }

    @Test
    void suspendedMemberCannotUpdateProfileEvenWithSignedJwt() throws Exception {
        em.find(User.class, user.getUserId()).changeStatus(UserStatus.SUSPENDED);
        em.flush();
        em.clear();

        mvc.perform(patch("/api/users/me").with(users.authenticated(user))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"newname\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
        assertNicknameUnchanged();
    }

    private void assertNicknameUnchanged() {
        em.clear();
        assertEquals(originalNickname,
                profiles.findByUser_UserId(user.getUserId()).orElseThrow().getNickname());
    }

    @AfterTransaction
    void fixtureAndApiChangesAreRolledBack() {
        assertTrue(accounts.findById(user.getUserId()).isEmpty());
        assertTrue(profiles.findByUser_UserId(user.getUserId()).isEmpty());
    }
}
