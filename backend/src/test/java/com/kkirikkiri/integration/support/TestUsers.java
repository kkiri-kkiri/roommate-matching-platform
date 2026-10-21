package com.kkirikkiri.integration.support;

import java.util.UUID;

import jakarta.persistence.EntityManager;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.kkirikkiri.global.security.JwtTokenProvider;
import com.kkirikkiri.user.entity.Dormitory;
import com.kkirikkiri.user.entity.Faculty;
import com.kkirikkiri.user.entity.User;
import com.kkirikkiri.user.entity.UserProfile;
import com.kkirikkiri.user.entity.UserRole;

/** Imported explicitly by ApiIntegrationTest; all writes join the caller's transaction. */
public class TestUsers {
    public static final String PASSWORD = "Test1234!";

    private final EntityManager em;
    private final PasswordEncoder encoder;
    private final JwtTokenProvider tokens;

    public TestUsers(EntityManager em, PasswordEncoder encoder, JwtTokenProvider tokens) {
        this.em = em;
        this.encoder = encoder;
        this.tokens = tokens;
    }

    public User createUser() {
        return createUser(UserRole.USER);
    }

    public User createUser(UserRole role) {
        User user = new User(encoder.encode(PASSWORD), UUID.randomUUID() + "@hknu.ac.kr", role);
        user.verifyEmail();
        em.persist(user);
        em.flush();
        return user;
    }

    public UserProfile createProfile(User user) {
        String key = UUID.randomUUID().toString().replace("-", "");
        Dormitory dormitory = new Dormitory(key.substring(0, 20), "dorm-" + key.substring(0, 20), true);
        Faculty faculty = new Faculty("faculty-" + key, true);
        em.persist(dormitory);
        em.persist(faculty);
        UserProfile profile = new UserProfile(user, key.substring(0, 8), "남자", dormitory,
                faculty, null, (short) 2003, "22", "1학년", "INTJ");
        em.persist(profile);
        em.flush();
        return profile;
    }

    /** Uses the real JWT signature/filter and DB account check, without a login/SMTP request. */
    public RequestPostProcessor authenticated(User user) {
        String token = tokens.createAccessToken(user.getUserId(), user.getRole());
        return request -> {
            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            return request;
        };
    }
}
