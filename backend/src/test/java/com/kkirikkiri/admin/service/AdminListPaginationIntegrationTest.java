package com.kkirikkiri.admin.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.stream.Stream;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.kkirikkiri.admin.dto.AdminCrawledScheduleListRequest;
import com.kkirikkiri.admin.dto.AdminMemberListRequest;
import com.kkirikkiri.calendar.entity.CalendarCategory;
import com.kkirikkiri.calendar.entity.CalendarEvent;
import com.kkirikkiri.calendar.entity.CalendarEventSourceType;
import com.kkirikkiri.calendar.repository.AdminCrawledScheduleQueryRepository;
import com.kkirikkiri.global.exception.CustomException;
import com.kkirikkiri.global.exception.ErrorCode;
import com.kkirikkiri.global.security.JwtTokenProvider;
import com.kkirikkiri.matching.dto.RoommatePageResponse;
import com.kkirikkiri.user.entity.User;
import com.kkirikkiri.user.entity.UserRole;
import com.kkirikkiri.user.entity.UserStatus;
import com.kkirikkiri.user.repository.AdminMemberQueryRepository;

/** Real JWT filters, Controller, Validator, Service and repositories against the forced H2 test DB. */
@SpringBootTest(properties = "spring.application.name=admin-pagination-test")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AdminListPaginationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired AdminService service;
    @Autowired JwtTokenProvider tokens;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactions;
    @MockitoSpyBean AdminMemberQueryRepository members;
    @MockitoSpyBean AdminCrawledScheduleQueryRepository schedules;

    enum ListKind {
        MEMBERS("/api/admin/members"), SCHEDULES("/api/admin/crawled-schedules");
        final String path;
        ListKind(String path) { this.path = path; }
    }

    long adminId, activeId, suspendedId, olderScheduleId, newerScheduleId;
    String key;

    @BeforeAll
    void fixtures() {
        key = "page-" + UUID.randomUUID().toString().substring(0, 8);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            User admin = new User("test-hash", "admin@pagination.invalid", UserRole.ADMIN);
            User active = new User("test-hash", key + "-active@test.invalid");
            User suspended = new User("test-hash", key + "-suspended@test.invalid");
            suspended.changeStatus(UserStatus.SUSPENDED);
            em.persist(admin); em.persist(active); em.persist(suspended);
            adminId = admin.getUserId(); activeId = active.getUserId(); suspendedId = suspended.getUserId();
            CalendarCategory school = new CalendarCategory("SCHOOL", "학교 일정", null, true, true);
            CalendarCategory dormitory = new CalendarCategory("DORMITORY", "기숙사 일정", null, true, true);
            em.persist(school); em.persist(dormitory);
            CalendarEvent older = schedule(school, "older", LocalDateTime.of(2026, 10, 19, 10, 0));
            CalendarEvent newer = schedule(school, "newer", LocalDateTime.of(2026, 10, 20, 10, 0));
            em.persist(older); em.persist(newer);
            em.persist(schedule(dormitory, "dormitory", LocalDateTime.of(2026, 10, 20, 11, 0)));
            olderScheduleId = older.getEventId(); newerScheduleId = newer.getEventId();
        });
    }

    private CalendarEvent schedule(CalendarCategory category, String suffix, LocalDateTime collectedAt) {
        LocalDate date = LocalDate.of(2026, 10, 20);
        return new CalendarEvent(null, category, key + " " + suffix, date, date, null, null, true,
                "collected content", CalendarEventSourceType.CRAWLED, "school source", null, collectedAt);
    }

    static Stream<Arguments> invalidPages() {
        return Stream.of(ListKind.values()).flatMap(kind -> Stream.of(
                new int[]{-1, 6}, new int[]{0, 0}, new int[]{0, -1}, new int[]{0, 101},
                new int[]{0, Integer.MAX_VALUE}, new int[]{Integer.MAX_VALUE, 6},
                new int[]{1073741824, 2}, new int[]{21474837, 100},
                new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE})
                .map(pair -> Arguments.of(kind, pair[0], pair[1])));
    }

    @ParameterizedTest @MethodSource("invalidPages")
    void httpRejectsInvalidPaginationBeforeListRepositoryAccess(ListKind kind, int page, int size) throws Exception {
        clearInvocations(members, schedules);
        mvc.perform(asAdmin(get(kind.path).param("page", String.valueOf(page)).param("size", String.valueOf(size))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        verifyNoInteractions(members, schedules);
    }

    @ParameterizedTest @MethodSource("invalidPages")
    void directServiceRejectsInvalidPaginationBeforeListRepositoryAccess(ListKind kind, int page, int size) {
        clearInvocations(members, schedules);
        CustomException failure = assertThrows(CustomException.class, () -> direct(kind, page, size));
        assertEquals(ErrorCode.VALIDATION_ERROR, failure.getErrorCode());
        verifyNoInteractions(members, schedules);
    }

    static Stream<Arguments> validPages() {
        return Stream.of(ListKind.values()).flatMap(kind -> Stream.of(
                new int[]{0, 1}, new int[]{0, 100}, new int[]{1, 1},
                new int[]{Integer.MAX_VALUE, 1}, new int[]{21474836, 100})
                .map(pair -> Arguments.of(kind, pair[0], pair[1])));
    }

    @ParameterizedTest @MethodSource("validPages")
    void httpAndDirectServiceAcceptValidBoundariesIncludingExactMaximumOffset(ListKind kind, int page, int size)
            throws Exception {
        JsonNode data = read(asAdmin(get(kind.path).param("page", String.valueOf(page)).param("size", String.valueOf(size))));
        RoommatePageResponse<?> direct = direct(kind, page, size);
        assertEquals(page, data.get("page").asInt());
        assertEquals(size, data.get("size").asInt());
        assertEquals(page, direct.page());
        assertEquals(size, direct.size());
        assertEquals(direct.totalElements(), data.get("totalElements").asLong());
        assertEquals(direct.content().size(), data.get("content").size());
        if (page > 1) {
            assertTrue(direct.content().isEmpty());
            assertEquals(0, data.get("content").size());
        } else {
            assertFalse(direct.content().isEmpty());
        }
    }

    @ParameterizedTest @EnumSource(ListKind.class)
    void omittedPaginationKeepsDefaults(ListKind kind) throws Exception {
        JsonNode data = read(asAdmin(get(kind.path)));
        RoommatePageResponse<?> direct = direct(kind, null, null);
        assertEquals(0, data.get("page").asInt());
        assertEquals(6, data.get("size").asInt());
        assertEquals(0, direct.page());
        assertEquals(6, direct.size());
        assertEquals(direct.totalElements(), data.get("totalElements").asLong());
    }

    @ParameterizedTest @EnumSource(ListKind.class)
    void directServiceRejectsNullRequestWithoutListQueries(ListKind kind) {
        clearInvocations(members, schedules);
        CustomException failure = assertThrows(CustomException.class, () -> {
            if (kind == ListKind.MEMBERS) service.getMembers(adminId, null);
            else service.getCrawledSchedules(adminId, null);
        });
        assertEquals(ErrorCode.VALIDATION_ERROR, failure.getErrorCode());
        verifyNoInteractions(members, schedules);
    }

    @ParameterizedTest @EnumSource(ListKind.class)
    void listEndpointsStillRequireAnActiveAdministrator(ListKind kind) throws Exception {
        clearInvocations(members, schedules);
        mvc.perform(get(kind.path)).andExpect(status().isUnauthorized());
        mvc.perform(get(kind.path).header("Authorization", "Bearer " + tokens.createAccessToken(activeId, UserRole.USER)))
                .andExpect(status().isForbidden());
        mvc.perform(get(kind.path).header("Authorization", "Bearer " + tokens.createAccessToken(suspendedId, UserRole.ADMIN)))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(members, schedules);
    }

    @Test
    void memberSearchStatusFilterAndLatestOrderSurvivePagination() throws Exception {
        JsonNode all = read(asAdmin(get(ListKind.MEMBERS.path).param("q", key).param("size", "1")));
        assertEquals(2, all.get("totalElements").asInt());
        assertEquals(suspendedId, all.get("content").get(0).get("id").asLong());
        assertTrue(all.get("content").get(0).get("nickname").isNull());
        JsonNode next = read(asAdmin(get(ListKind.MEMBERS.path).param("q", key).param("size", "1").param("page", "1")));
        assertEquals(activeId, next.get("content").get(0).get("id").asLong());
        JsonNode filtered = read(asAdmin(get(ListKind.MEMBERS.path).param("q", key).param("status", "ACTIVE")));
        assertEquals(1, filtered.get("totalElements").asInt());
        assertEquals(activeId, filtered.get("content").get(0).get("id").asLong());
        assertEquals(key + "-active@test.invalid", filtered.get("content").get(0).get("schoolEmail").asString());
    }

    @Test
    void scheduleSearchCategoryFilterAndCollectedOrderSurvivePagination() throws Exception {
        MockHttpServletRequestBuilder first = get(ListKind.SCHEDULES.path).param("q", key).param("category", "SCHOOL").param("size", "1");
        JsonNode data = read(asAdmin(first));
        assertEquals(2, data.get("totalElements").asInt());
        assertEquals(newerScheduleId, data.get("content").get(0).get("id").asLong());
        assertEquals("SCHOOL", data.get("content").get(0).get("category").asString());
        assertEquals("2026-10-20", data.get("content").get(0).get("date").asString());
        assertEquals("collected content", data.get("content").get(0).get("content").asString());
        assertEquals("school source", data.get("content").get(0).get("source").asString());
        JsonNode next = read(asAdmin(get(ListKind.SCHEDULES.path).param("q", key).param("category", "SCHOOL")
                .param("size", "1").param("page", "1")));
        assertEquals(olderScheduleId, next.get("content").get(0).get("id").asLong());
    }

    private RoommatePageResponse<?> direct(ListKind kind, Integer page, Integer size) {
        return kind == ListKind.MEMBERS
                ? service.getMembers(adminId, new AdminMemberListRequest(page, size, null, null, null))
                : service.getCrawledSchedules(adminId, new AdminCrawledScheduleListRequest(page, size, null, null));
    }

    private MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + tokens.createAccessToken(adminId, UserRole.ADMIN));
    }

    private JsonNode read(MockHttpServletRequestBuilder request) throws Exception {
        return mapper.readTree(mvc.perform(request).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).get("data");
    }
}
