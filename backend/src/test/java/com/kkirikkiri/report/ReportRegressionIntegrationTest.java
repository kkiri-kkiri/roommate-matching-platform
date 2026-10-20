package com.kkirikkiri.report;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import com.kkirikkiri.admin.dto.AdminReportListRequest;
import com.kkirikkiri.admin.dto.AdminReportProcessRequest;
import com.kkirikkiri.admin.service.AdminService;
import com.kkirikkiri.global.exception.CustomException;
import com.kkirikkiri.global.exception.ErrorCode;
import com.kkirikkiri.global.security.JwtTokenProvider;
import com.kkirikkiri.report.dto.ReportCreateRequest;
import com.kkirikkiri.report.entity.Report;
import com.kkirikkiri.report.entity.ReportStatus;
import com.kkirikkiri.report.service.ReportService;
import com.kkirikkiri.user.entity.*;

/** Actual HTTP validation, services and persistence, only against the forced test H2 memory database. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReportRegressionIntegrationTest {
    @Autowired ReportService reports;
    @Autowired AdminService admins;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtTokenProvider tokens;
    long reporter, target, admin;

    @BeforeEach
    void fixtures() {
        tx(() -> {
            String key = UUID.randomUUID().toString();
            var r = new User("test-hash", key + "r@test.invalid");
            var t = new User("test-hash", key + "t@test.invalid");
            var a = new User("test-hash", key + "a@test.invalid", UserRole.ADMIN);
            em.persist(r); em.persist(t); em.persist(a);
            reporter = r.getUserId(); target = t.getUserId(); admin = a.getUserId();
            return null;
        });
    }

    static Stream<Arguments> descriptionBoundaries() {
        return Stream.of("a", "가", "😀").flatMap(unit -> Stream.of(499, 500, 501, 70000)
                .map(length -> Arguments.of(unit, length)));
    }

    @ParameterizedTest
    @MethodSource("descriptionBoundaries")
    void directApiAndServiceEnforceTheSameUtf16Limit(String unit, int length) throws Exception {
        String description = unit.repeat(length / unit.length()) + "a".repeat(length % unit.length());
        var body = new ReportCreateRequest("USER", target, "기타", description);
        if (length > 500) {
            mvc.perform(post("/api/reports").header("Authorization", bearer(reporter, UserRole.USER))
                    .contentType("application/json").content(json.writeValueAsString(body)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
            assertError(ErrorCode.VALIDATION_ERROR, () -> reports.createReport(reporter, body));
            assertEquals(0, countReports());
        } else {
            long id = reports.createReport(reporter, body);
            assertEquals(description, jdbc.queryForObject("select description from report where report_id=?", String.class, id));
            // A distinct authenticated reporter exercises HTTP storage instead of duplicate rejection.
            mvc.perform(post("/api/reports").header("Authorization", bearer(admin, UserRole.ADMIN))
                    .contentType("application/json").content(json.writeValueAsString(body)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data").isNumber());
            assertEquals(1, jdbc.queryForObject("select count(*) from report where reporter_id=? and description=?",
                    Integer.class, admin, description));
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void nullAndBlankDescriptionPoliciesArePreservedWithoutTrimming(String description) throws Exception {
        var other = new ReportCreateRequest("USER", target, "기타", description);
        assertError(ErrorCode.VALIDATION_ERROR, () -> reports.createReport(reporter, other));
        mvc.perform(post("/api/reports").header("Authorization", bearer(reporter, UserRole.USER))
                .contentType("application/json").content(json.writeValueAsString(other)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        long id = reports.createReport(reporter, new ReportCreateRequest("USER", target, "욕설·비방", description));
        assertEquals(description, jdbc.queryForObject("select description from report where report_id=?", String.class, id));
    }

    @Test
    void creationEndpointRequiresPrincipalAndIgnoresClientReporterId() throws Exception {
        var body = Map.of("targetType", "USER", "targetId", target, "reason", "욕설·비방", "reporterId", admin);
        mvc.perform(post("/api/reports").contentType("application/json").content(json.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/reports").header("Authorization", bearer(reporter, UserRole.USER))
                .contentType("application/json").content(json.writeValueAsString(body)))
                .andExpect(status().isOk());
        assertEquals(1, countReports());
        assertEquals(0, jdbc.queryForObject("select count(*) from report where reporter_id=?", Integer.class, admin));
        mvc.perform(get("/api/admin/reports").header("Authorization", bearer(reporter, UserRole.USER)))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 50000000, 100000000, 1100000000})
    void immediateProcessingAfterDatabaseReloadUsesWholeSeconds(long elapsedNanos) {
        LocalDateTime created = LocalDateTime.of(2026, 10, 20, 12, 0, 0, 900000000);
        long id;
        try (var clock = mockStatic(LocalDateTime.class, CALLS_REAL_METHODS)) {
            clock.when(LocalDateTime::now).thenReturn(created);
            id = reports.createReport(reporter, new ReportCreateRequest("USER", target, "욕설·비방", null));
        }
        tx(() -> {
            assertEquals(created.truncatedTo(ChronoUnit.SECONDS), em.find(Report.class, id).getCreatedAt());
            return null;
        });
        LocalDateTime processed = created.plusNanos(elapsedNanos);
        try (var clock = mockStatic(LocalDateTime.class, CALLS_REAL_METHODS)) {
            clock.when(LocalDateTime::now).thenReturn(processed);
            admins.processReport(admin, id, new AdminReportProcessRequest("COMPLETE"));
        }
        tx(() -> {
            var r = em.find(Report.class, id);
            assertEquals(ReportStatus.COMPLETED, r.getStatus());
            assertEquals(created.truncatedTo(ChronoUnit.SECONDS), r.getCreatedAt());
            assertEquals(processed.truncatedTo(ChronoUnit.SECONDS), r.getProcessedAt());
            return null;
        });
        // The existing API timestamp type is retained; normalized values have no fractional second.
        assertEquals(created.truncatedTo(ChronoUnit.SECONDS), admins.getReport(admin, id).createdAt());
    }

    @Test
    void sameSecondReportsRemainSortedByIdAndListTimestampsStayStable() {
        LocalDateTime time = LocalDateTime.of(2026, 10, 20, 12, 0, 0, 900000000);
        long first, second;
        try (var clock = mockStatic(LocalDateTime.class, CALLS_REAL_METHODS)) {
            clock.when(LocalDateTime::now).thenReturn(time);
            first = reports.createReport(reporter, new ReportCreateRequest("USER", target, "욕설·비방", null));
            second = reports.createReport(admin, new ReportCreateRequest("USER", target, "욕설·비방", null));
        }
        var page = admins.getReports(admin, new AdminReportListRequest(0, 100, null, "USER", "PENDING"));
        var pair = page.content().stream().filter(r -> r.id() == first || r.id() == second).toList();
        assertEquals(java.util.List.of(second, first), pair.stream().map(r -> r.id()).toList());
        assertTrue(pair.stream().allMatch(r -> r.createdAt().equals(time.withNano(0))));
    }

    private String bearer(long user, UserRole role) { return "Bearer " + tokens.createAccessToken(user, role); }
    private int countReports() {
        return jdbc.queryForObject("select count(*) from report where reporter_id=?", Integer.class, reporter);
    }
    private <T> T tx(Supplier<T> action) { return new TransactionTemplate(manager).execute(s -> action.get()); }
    private static void assertError(ErrorCode expected, Runnable action) {
        assertEquals(expected, assertThrows(CustomException.class, action::run).getErrorCode());
    }
}
