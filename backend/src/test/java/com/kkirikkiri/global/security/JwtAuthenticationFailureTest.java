package com.kkirikkiri.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.sql.SQLTransientConnectionException;
import java.util.Optional;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.DispatcherType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kkirikkiri.global.config.SecurityConfig;
import com.kkirikkiri.user.entity.User;
import com.kkirikkiri.user.entity.UserRole;
import com.kkirikkiri.user.entity.UserStatus;
import com.kkirikkiri.user.repository.UserRepository;

@WebMvcTest(controllers = JwtAuthenticationFailureTest.Probe.class, properties = {
        "jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "jwt.access-token-expiration-seconds=3600"
})
@Import({SecurityConfig.class, JwtAuthenticationProvider.class, JwtTokenProvider.class,
        JwtAuthenticationFailureTest.Probe.class})
class JwtAuthenticationFailureTest {
    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokens;
    @MockitoBean UserRepository users;

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void databaseFailureIsSafe500AndSameJwtRecovers(boolean transactionFailure) throws Exception {
        String jwt = tokens.createAccessToken(1L, UserRole.USER);
        String sensitive = "jdbc:mysql://private-host/db?password=secret " + jwt;
        var cause = new SQLTransientConnectionException(sensitive);
        RuntimeException failure = transactionFailure
                ? new CannotCreateTransactionException(sensitive, cause)
                : new DataAccessResourceFailureException(sensitive, cause);
        when(users.findById(1L)).thenThrow(failure);
        Logger logger = (Logger) LoggerFactory.getLogger(JwtAuthenticationFilter.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            var result = mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + jwt))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.errorCode").value("INTERNAL_SERVER_ERROR"))
                    .andExpect(header().exists("X-Request-ID")).andReturn();
            String trace = result.getResponse().getHeader("X-Request-ID");
            assertThat(trace).matches("[0-9a-f-]{36}");
            assertThat(result.getResponse().getContentAsString()).doesNotContain(jwt, "private-host", "secret", "jdbc:");
            assertThat(appender.list).hasSize(1);
            var event = appender.list.getFirst();
            assertThat(event.getFormattedMessage()).contains(trace, failure.getClass().getName(), cause.getClass().getName())
                    .doesNotContain(jwt, "private-host", "secret", "jdbc:");
            assertThat(event.getThrowableProxy()).isNull();
            User user = mock(User.class);
            when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
            doReturn(Optional.of(user)).when(users).findById(1L);
            mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + jwt)).andExpect(status().isOk());
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void onlyErrorRendererAllowsContainerErrorDispatch() throws Exception {
        mvc.perform(get("/error")).andExpect(status().isUnauthorized());
        mvc.perform(get("/error").with(request -> { request.setDispatcherType(DispatcherType.ERROR); return request; }))
                .andExpect(status().isInternalServerError()).andExpect(content().string("safe error"));
        mvc.perform(get("/api/users/me").with(request -> { request.setDispatcherType(DispatcherType.ERROR); return request; })).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/members").with(request -> { request.setDispatcherType(DispatcherType.ERROR); return request; })).andExpect(status().isUnauthorized());
        String jwt = tokens.createAccessToken(1L, UserRole.USER);
        User user = mock(User.class);
        when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
        when(users.findById(1L)).thenReturn(Optional.of(user));
        mvc.perform(get("/error").header("Authorization", "Bearer " + jwt)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/members").header("Authorization", "Bearer " + jwt)).andExpect(status().isForbidden());
    }

    @RestController
    static class Probe {
        @GetMapping("/api/users/me") String me() { return "authenticated"; }
        @GetMapping("/error") ResponseEntity<String> error() { return ResponseEntity.internalServerError().body("safe error"); }
    }
}
