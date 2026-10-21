package com.kkirikkiri.integration.calendar;

import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import java.util.Map;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import com.kkirikkiri.calendar.entity.*;
import com.kkirikkiri.calendar.repository.*;
import com.kkirikkiri.integration.support.ApiIntegrationTest;
import com.kkirikkiri.integration.support.TestUsers;
import com.kkirikkiri.user.entity.User;

@ApiIntegrationTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CalendarApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired TestUsers users;
    @Autowired EntityManager em;
    @Autowired JsonMapper json;
    @Autowired CalendarEventRepository events;
    @Autowired CalendarCategoryRepository categories;
    @MockitoSpyBean CalendarCategorySettingRepository settings;
    @Autowired PlatformTransactionManager manager;
    @Autowired JdbcTemplate jdbc;
    TransactionTemplate tx;

    User owner, other;
    Long schoolId, categoryId;
    String categoryCode;
    final LocalDate day = LocalDate.of(2026, 10, 21);

    @BeforeEach
    void setup() throws Exception {
        tx = new TransactionTemplate(manager);
        tx.executeWithoutResult(s -> {
            owner = users.createUser();
            other = users.createUser();
            var school = new CalendarCategory("SCHOOL", "학교", null, true, true);
            em.persist(school);
            schoolId = school.getCategoryId();
        });
        var result = mvc.perform(post("/api/calendar/categories/custom").with(users.authenticated(owner))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"label":"스터디","color":"#123456"}
                                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.fixed").value(false))
                .andExpect(jsonPath("$.data.enabled").value(true)).andReturn();
        var data = json.readTree(result.getResponse().getContentAsString()).get("data");
        categoryId = data.get("id").asLong();
        categoryCode = data.get("code").asText();
        assertOutsideTransaction();
        assertEquals(owner.getUserId(), categories.findById(categoryId).orElseThrow().getOwnerUser().getUserId());
        assertTrue(settings.findByUser_UserIdAndCategory_CategoryId(owner.getUserId(), categoryId).isPresent());
    }

    @AfterEach
    void cleanup() {
        tx.executeWithoutResult(s -> {
            jdbc.update("delete from CALENDAR_EVENT where owner_user_id in (?,?) or category_id=?",
                    owner.getUserId(), other.getUserId(), schoolId);
            jdbc.update("delete from CALENDAR_CATEGORY_SETTING where user_id in (?,?) or category_id=?",
                    owner.getUserId(), other.getUserId(), schoolId);
            jdbc.update("delete from CALENDAR_CATEGORY where owner_user_id in (?,?) or category_id=?",
                    owner.getUserId(), other.getUserId(), schoolId);
            jdbc.update("delete from `USER` where user_id in (?,?)", owner.getUserId(), other.getUserId());
        });
    }

    @Test
    void eventAndCategoryLifecyclePersistsPatchMergeAndDeletesDependents() throws Exception {
        long id = createEvent(owner, categoryId);
        mvc.perform(patch("/api/calendar/events/{id}", id).with(users.authenticated(owner))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"endDate":"2026-10-22","title":null,"startTime":null,"description":null}
                                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.title").value("만남"))
                .andExpect(jsonPath("$.data.endDate").value("2026-10-22"))
                .andExpect(jsonPath("$.data.startTime").value("10:00"))
                .andExpect(jsonPath("$.data.endTime").value(org.hamcrest.Matchers.nullValue()));
        assertOutsideTransaction();
        var saved = events.findById(id).orElseThrow();
        assertEquals(day.plusDays(1), saved.getEndDate());
        assertEquals("장소", saved.getDescription());
        assertFalse(saved.isAllDay());
        mvc.perform(patch("/api/calendar/categories/{id}", categoryId).with(users.authenticated(owner))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"label\":\"전공 스터디\",\"enabled\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.color").value("#123456"));
        assertOutsideTransaction();
        assertEquals("전공 스터디", categories.findById(categoryId).orElseThrow().getDefaultName());
        assertFalse(settings.findByUser_UserIdAndCategory_CategoryId(owner.getUserId(), categoryId).orElseThrow().isEnabled());
        mvc.perform(delete("/api/calendar/categories/{id}", categoryId).with(users.authenticated(owner)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        mvc.perform(delete("/api/calendar/events/{id}", id).with(users.authenticated(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));
        assertOutsideTransaction();
        assertFalse(events.existsById(id));
        mvc.perform(delete("/api/calendar/categories/{id}", categoryId).with(users.authenticated(owner)))
                .andExpect(status().isOk());
        assertOutsideTransaction();
        assertFalse(categories.existsById(categoryId));
        assertTrue(settings.findByUser_UserIdAndCategory_CategoryId(owner.getUserId(), categoryId).isEmpty());
    }

    @Test
    void periodAndCategoryQueriesIncludeBoundaryOverlapWithoutLeakingPrivateEvents() throws Exception {
        long mine = createEvent(owner, categoryId);
        var publicEvent = tx.execute(s -> {
            var foreignCategory = new CalendarCategory("CUSTOM-other", "타인", em.find(User.class, other.getUserId()), false, false);
            em.persist(foreignCategory);
            em.persist(new CalendarEvent(em.find(User.class, other.getUserId()), foreignCategory, "비공개", day, day, null, null, true,
                null, CalendarEventSourceType.USER, null, null, null));
            var collected = new CalendarEvent(null, em.find(CalendarCategory.class, schoolId), "학사", day.minusDays(2),
                day, null, null, true, null, CalendarEventSourceType.CRAWLED, "학교 홈페이지", null, null);
            em.persist(collected);
            em.persist(new CalendarEvent(null, em.find(CalendarCategory.class, schoolId), "범위 밖", day.plusDays(1),
                day.plusDays(1), null, null, true, null, CalendarEventSourceType.CRAWLED, "학교 홈페이지", null, null));
            return collected;
        });
        assertOutsideTransaction();
        mvc.perform(get("/api/calendar/events").param("from", day.toString()).param("to", day.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(publicEvent.getEventId()))
                .andExpect(jsonPath("$.data[0].editable").value(false));
        mvc.perform(get("/api/calendar/events").with(users.authenticated(owner))
                        .param("from", day.toString()).param("to", day.toString()).param("categories", "SCHOOL," + categoryCode))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[*].id",
                        containsInAnyOrder(publicEvent.getEventId().intValue(), (int) mine)));
        mvc.perform(get("/api/calendar/events").with(users.authenticated(other))
                        .param("from", day.toString()).param("to", day.toString()).param("categories", categoryCode))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void foreignAndCrawledEventsRejectMutationAndPreserveStoredState() throws Exception {
        long mine = createEvent(owner, categoryId);
        var crawled = tx.execute(s -> {
            var collected = new CalendarEvent(null, em.find(CalendarCategory.class, schoolId), "학사", day, day,
                null, null, true, null, CalendarEventSourceType.CRAWLED, "학교", null, null);
            em.persist(collected);
            return collected;
        });
        assertOutsideTransaction();
        for (long id : new long[] {mine, crawled.getEventId()}) {
            mvc.perform(patch("/api/calendar/events/{id}", id).with(users.authenticated(other))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"변경\"}"))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
            mvc.perform(delete("/api/calendar/events/{id}", id).with(users.authenticated(other)))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/calendar/events").with(users.authenticated(other))
                        .contentType(MediaType.APPLICATION_JSON).content(eventBody(categoryId)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/calendar/events").with(users.authenticated(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(eventBody(schoolId)))
                .andExpect(status().isForbidden());
        assertOutsideTransaction();
        assertEquals("만남", events.findById(mine).orElseThrow().getTitle());
        assertEquals("학사", events.findById(crawled.getEventId()).orElseThrow().getTitle());
        assertEquals(2, events.count());
    }

    @Test
    void categorySettingsArePersonalAndOmittedEntriesRemainStored() throws Exception {
        mvc.perform(put("/api/calendar/categories").with(users.authenticated(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(new Object[] {
                                Map.of("id", schoolId, "label", "학교", "color", "#654321", "enabled", false)})))
                .andExpect(status().isOk());
        assertOutsideTransaction();
        assertEquals("#654321", settings.findByUser_UserIdAndCategory_CategoryId(owner.getUserId(), schoolId).orElseThrow().getColor());
        assertTrue(settings.findByUser_UserIdAndCategory_CategoryId(owner.getUserId(), categoryId).isPresent());
        long before = settings.count();
        mvc.perform(get("/api/calendar/categories").with(users.authenticated(other)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].color").value("#245fc5"))
                .andExpect(jsonPath("$.data[0].enabled").value(true));
        assertOutsideTransaction();
        assertEquals(before, settings.count(), "category reads must not create settings");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"title\":\" \"}", "{\"startTime\":\"24:00\"}",
            "{\"startTime\":\"12:00\",\"endTime\":\"11:00\"}", "{\"endDate\":\"2026-10-20\"}",
            "{\"startDate\":\"1899-12-31\"}"})
    void invalidPatchIncludingMergedDateAndTimeLeavesEventUnchanged(String body) throws Exception {
        long id = createEvent(owner, categoryId);
        mvc.perform(patch("/api/calendar/events/{id}", id).with(users.authenticated(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        assertOutsideTransaction();
        var saved = events.findById(id).orElseThrow();
        assertEquals("만남", saved.getTitle());
        assertEquals(day, saved.getStartDate());
        assertEquals(day, saved.getEndDate());
        assertEquals(java.time.LocalTime.of(10, 0), saved.getStartTime());
        assertNull(saved.getEndTime());
    }

    @Test
    void batchValidationRejectsWholeRequestWithoutChangingEarlierEntry() throws Exception {
        mvc.perform(put("/api/calendar/categories").with(users.authenticated(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(new Object[] {
                                Map.of("id", categoryId, "label", "변경", "color", "#654321", "enabled", false),
                                Map.of("id", schoolId, "label", "학교", "color", "invalid", "enabled", true)})))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        assertOutsideTransaction();
        var setting = settings.findByUser_UserIdAndCategory_CategoryId(owner.getUserId(), categoryId).orElseThrow();
        assertEquals("스터디", setting.getDisplayName());
        assertEquals("#123456", setting.getColor());
        assertTrue(setting.isEnabled());
        assertTrue(settings.findByUser_UserIdAndCategory_CategoryId(owner.getUserId(), schoolId).isEmpty());
    }

    @Test
    void authenticationAndQueryBindingRejectInvalidRequests() throws Exception {
        mvc.perform(post("/api/calendar/events").contentType(MediaType.APPLICATION_JSON).content(eventBody(categoryId)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/calendar/categories")).andExpect(status().isUnauthorized());
        for (String from : new String[] {"bad-date", "2026-10-22"}) {
            mvc.perform(get("/api/calendar/events").param("from", from).param("to", day.toString()))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        }
        mvc.perform(get("/api/calendar/events").param("to", day.toString()))
                .andExpect(status().isBadRequest());
        assertOutsideTransaction();
        assertEquals(0, events.count());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PATCH /events", "DELETE /events", "PUT /categories",
            "POST /categories/custom", "PATCH /categories", "DELETE /categories"})
    void unauthenticatedMutationsPreserveCommittedEventsCategoriesAndSettings(String operation) throws Exception {
        long eventId = createEvent(owner, categoryId);
        var before = snapshot();
        String[] parts = operation.split(" ", 2);
        String path = "/api/calendar" + parts[1];
        if (parts[1].equals("/events")) path += "/" + eventId;
        else if (parts[1].equals("/categories") && !parts[0].equals("PUT")) path += "/" + categoryId;
        String body = parts[0].equals("PUT") ? json.writeValueAsString(List.of(
                Map.of("id", categoryId, "label", "변경", "color", "#654321", "enabled", false)))
                : "{\"title\":\"변경\",\"label\":\"변경\",\"color\":\"#654321\"}";
        mvc.perform(request(HttpMethod.valueOf(parts[0]), path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
        assertEquals(before, snapshot());
    }

    @ParameterizedTest
    @CsvSource({"PATCH,false,403", "DELETE,false,403", "PUT,false,403",
            "PATCH,true,403", "DELETE,true,403", "PUT,true,400"})
    void foreignAndSystemCategoryMutationsPreserveCommittedData(String method, boolean system, int expectedStatus) throws Exception {
        Long id = schoolId;
        if (!system) {
            var result = mvc.perform(post("/api/calendar/categories/custom").with(users.authenticated(other))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"label\":\"타인\",\"color\":\"#123456\"}"))
                    .andExpect(status().isOk()).andReturn();
            id = json.readTree(result.getResponse().getContentAsString()).get("data").get("id").asLong();
        }
        var before = snapshot();
        String path = "/api/calendar/categories" + (method.equals("PUT") ? "" : "/" + id);
        String body = method.equals("PUT") ? json.writeValueAsString(List.of(
                Map.of("id", id, "label", "변경", "color", "#654321", "enabled", false))) : "{\"label\":\"변경\"}";
        mvc.perform(request(HttpMethod.valueOf(method), path).with(users.authenticated(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expectedStatus)).andExpect(jsonPath("$.errorCode")
                        .value(expectedStatus == 400 ? "VALIDATION_ERROR" : "FORBIDDEN"));
        assertEquals(before, snapshot());
    }

    @Test
    void categoryCreationRollsBackBothRowsAfterFlushedSettingFailure() throws Exception {
        var before = snapshot();
        var insertedCategory = new AtomicReference<Long>();
        org.mockito.Mockito.doAnswer(call -> {
            CalendarCategorySetting saved = (CalendarCategorySetting) org.mockito.Mockito.mockingDetails(settings)
                    .getMockCreationSettings().getDefaultAnswer().answer(call);
            settings.flush();
            insertedCategory.set(saved.getCategory().getCategoryId());
            assertEquals(1, jdbc.queryForObject("select count(*) from CALENDAR_CATEGORY where category_id=?",
                    Integer.class, insertedCategory.get()));
            assertEquals(1, jdbc.queryForObject("select count(*) from CALENDAR_CATEGORY_SETTING where category_id=?",
                    Integer.class, insertedCategory.get()));
            throw new DataAccessResourceFailureException("synthetic calendar storage failure");
        }).when(settings).save(org.mockito.ArgumentMatchers.any(CalendarCategorySetting.class));
        mvc.perform(post("/api/calendar/categories/custom").with(users.authenticated(owner))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"label\":\"롤백\",\"color\":\"#123456\"}"))
                .andExpect(status().isInternalServerError());
        assertNotNull(insertedCategory.get(), "both inserts must have been flushed before the injected failure");
        assertFalse(categories.existsById(insertedCategory.get()));
        assertEquals(0, jdbc.queryForObject("select count(*) from CALENDAR_CATEGORY_SETTING where category_id=?",
                Integer.class, insertedCategory.get()));
        assertEquals(before, snapshot());
    }

    private Map<String, Object> snapshot() {
        return tx.execute(s -> Map.of(
                "events", jdbc.queryForList("select * from CALENDAR_EVENT where owner_user_id in (?,?) or category_id=? order by event_id",
                        owner.getUserId(), other.getUserId(), schoolId),
                "categories", jdbc.queryForList("select * from CALENDAR_CATEGORY where owner_user_id in (?,?) or category_id=? order by category_id",
                        owner.getUserId(), other.getUserId(), schoolId),
                "settings", jdbc.queryForList("select * from CALENDAR_CATEGORY_SETTING where user_id in (?,?) or category_id=? order by category_setting_id",
                        owner.getUserId(), other.getUserId(), schoolId)));
    }

    private long createEvent(User user, Long category) throws Exception {
        var result = mvc.perform(post("/api/calendar/events").with(users.authenticated(user))
                        .contentType(MediaType.APPLICATION_JSON).content(eventBody(category)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.editable").value(true)).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("data").get("id").asLong();
    }

    private String eventBody(Long category) {
        return """
                {"title":"만남","startDate":"2026-10-21","endDate":"2026-10-21",
                 "startTime":"10:00","categoryId":%d,"description":"장소"}
                """.formatted(category);
    }

    /** Every check runs after the HTTP service committed, in a new transaction. */
    private void assertOutsideTransaction() { assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()); }
}
