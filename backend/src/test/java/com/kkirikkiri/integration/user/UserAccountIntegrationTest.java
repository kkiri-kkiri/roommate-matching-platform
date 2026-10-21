package com.kkirikkiri.integration.user;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import com.kkirikkiri.integration.support.ApiIntegrationTest;
import com.kkirikkiri.integration.support.TestUsers;
import com.kkirikkiri.user.entity.Faculty;
import com.kkirikkiri.user.entity.Major;
import com.kkirikkiri.user.entity.User;
import com.kkirikkiri.user.entity.UserProfile;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ApiIntegrationTest
class UserAccountIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired TestUsers users;
    @Autowired EntityManager em;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder encoder;
    User owner, other;
    Long profileId, otherProfileId;

    @BeforeEach
    void setUp() {
        owner = users.createUser();
        profileId = users.createProfile(owner).getUserProfileId();
        other = users.createUser();
        otherProfileId = users.createProfile(other).getUserProfileId();
        em.clear();
    }

    @Test
    void partialUpdatePreservesNullAndReadOnlyFieldsAndUsesJwtOwner() throws Exception {
        var before = em.find(UserProfile.class, profileId);
        var major = new Major(before.getFaculty(), "보존전공", true);
        em.persist(major);
        before.changeAcademicAffiliation(before.getFaculty(), major);
        em.flush();
        String nickname = before.getNickname();
        String otherNickname = em.find(UserProfile.class, otherProfileId).getNickname();
        String dormitory = before.getDormitory().getDormitoryName();
        var request = new LinkedHashMap<String, Object>(Map.of(
                "userId", other.getUserId(), "mbtiEnergy", "E", "schoolEmail", other.getSchoolEmail(),
                "gender", "여자", "birthYear", 1990, "admissionYear", "99"));
        request.put("nickname", null);
        request.put("major", null);
        mvc.perform(patch("/api/users/me").with(users.authenticated(owner))
                        .contentType("application/json").content(json.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(owner.getUserId()))
                .andExpect(jsonPath("$.data.nickname").value(nickname))
                .andExpect(jsonPath("$.data.mbtiEnergy").value("E"));
        em.flush();
        em.clear();
        mvc.perform(get("/api/users/me").with(users.authenticated(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value(nickname))
                .andExpect(jsonPath("$.data.schoolEmail").value(owner.getSchoolEmail()))
                .andExpect(jsonPath("$.data.gender").value("남자"))
                .andExpect(jsonPath("$.data.birthYear").value(2003))
                .andExpect(jsonPath("$.data.admissionYear").value("22"))
                .andExpect(jsonPath("$.data.dormitory").value(dormitory))
                .andExpect(jsonPath("$.data.major").value("보존전공"))
                .andExpect(jsonPath("$.data.mbtiInformation").value("N"))
                .andExpect(jsonPath("$.data.mbtiDecision").value("T"))
                .andExpect(jsonPath("$.data.mbtiLifestyle").value("J"));
        assertEquals("ENTJ", em.find(UserProfile.class, profileId).getMbti());
        assertEquals(major.getMajorId(), em.find(UserProfile.class, profileId).getMajor().getMajorId());
        assertEquals(otherNickname, em.find(UserProfile.class, otherProfileId).getNickname());
        assertEquals("INTJ", em.find(UserProfile.class, otherProfileId).getMbti());
    }

    @Test
    void explicitNoMajorAllowsMovingToFacultyWithoutMajors() throws Exception {
        Faculty target = facultyAndExistingMajor();
        mvc.perform(patch("/api/users/me").with(users.authenticated(owner)).contentType("application/json")
                        .content(json.writeValueAsString(Map.of("faculty", target.getFacultyName(), "major", "세부전공 없음"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.major").doesNotExist());
        em.flush();
        em.clear();
        var saved = em.find(UserProfile.class, profileId);
        assertNull(saved.getMajor());
        assertEquals(target.getFacultyId(), saved.getFaculty().getFacultyId());
    }

    @Test
    void wrongFacultyCannotPartiallyApplyNicknameOrDiscardExistingMajor() throws Exception {
        Faculty target = facultyAndExistingMajor();
        var before = em.find(UserProfile.class, profileId);
        String nickname = before.getNickname();
        Long majorId = before.getMajor().getMajorId();
        Long facultyId = before.getFaculty().getFacultyId();
        mvc.perform(patch("/api/users/me").with(users.authenticated(owner)).contentType("application/json")
                        .content(json.writeValueAsString(Map.of("faculty", target.getFacultyName(), "nickname", "변경거절"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        em.flush();
        em.clear();
        var saved = em.find(UserProfile.class, profileId);
        assertEquals(nickname, saved.getNickname());
        assertEquals(majorId, saved.getMajor().getMajorId());
        assertEquals(facultyId, saved.getFaculty().getFacultyId());
    }

    @Test
    void duplicateNicknameRejectsOtherRequestedChanges() throws Exception {
        String nickname = em.find(UserProfile.class, profileId).getNickname();
        String duplicate = em.find(UserProfile.class, otherProfileId).getNickname();
        mvc.perform(patch("/api/users/me").with(users.authenticated(owner)).contentType("application/json")
                        .content(json.writeValueAsString(Map.of("nickname", duplicate, "mbtiEnergy", "E"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        em.flush();
        em.clear();
        assertEquals(nickname, em.find(UserProfile.class, profileId).getNickname());
        assertEquals("INTJ", em.find(UserProfile.class, profileId).getMbti());
    }

    @Test
    void passwordChangePersistsOnlyJwtOwnersEncodedPassword() throws Exception {
        String otherHash = em.find(User.class, other.getUserId()).getPassword();
        mvc.perform(patch("/api/users/me/password").with(users.authenticated(owner)).contentType("application/json")
                        .content(json.writeValueAsString(Map.of("currentPassword", TestUsers.PASSWORD,
                                "newPassword", "Changed123!", "newPasswordConfirm", "Changed123!", "userId", other.getUserId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());
        em.flush();
        em.clear();
        String saved = em.find(User.class, owner.getUserId()).getPassword();
        assertTrue(encoder.matches("Changed123!", saved));
        assertFalse(encoder.matches(TestUsers.PASSWORD, saved));
        assertNotEquals("Changed123!", saved);
        assertEquals(otherHash, em.find(User.class, other.getUserId()).getPassword());
    }

    @ParameterizedTest
    @CsvSource({"Wrong123!,Changed123!,INVALID_REQUEST", "Test1234!,short,VALIDATION_ERROR"})
    void rejectedPasswordChangePreservesStoredHash(String current, String next, String code) throws Exception {
        String hash = em.find(User.class, owner.getUserId()).getPassword();
        mvc.perform(patch("/api/users/me/password").with(users.authenticated(owner)).contentType("application/json")
                        .content(json.writeValueAsString(Map.of("currentPassword", current, "newPassword", next, "newPasswordConfirm", next))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value(code));
        em.flush();
        em.clear();
        assertEquals(hash, em.find(User.class, owner.getUserId()).getPassword());
    }

    private Faculty facultyAndExistingMajor() {
        var profile = em.find(UserProfile.class, profileId);
        var major = new Major(profile.getFaculty(), "기존전공", true);
        em.persist(major);
        profile.changeAcademicAffiliation(profile.getFaculty(), major);
        var target = new Faculty("target-" + UUID.randomUUID(), true);
        em.persist(target);
        em.flush();
        em.clear();
        return target;
    }
}
