package com.kkirikkiri.calendar.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.kkirikkiri.admin.service.AdminService;
import com.kkirikkiri.calendar.crawler.*;
import com.kkirikkiri.calendar.dto.*;
import com.kkirikkiri.calendar.entity.*;
import com.kkirikkiri.calendar.repository.*;
import com.kkirikkiri.global.exception.*;
import com.kkirikkiri.user.entity.*;
import com.kkirikkiri.user.repository.UserRepository;

/** Real H2 transactions; only HTTP and deliberately injected DB failures are mocked. */
@SpringBootTest
@ActiveProfiles("test")
class CalendarIntegrationTest {
    @Autowired CalendarService calendar;
    @Autowired CalendarCrawlingService crawling;
    @Autowired AdminService admins;
    @Autowired CalendarCategoryRepository categories;
    @Autowired CalendarCategorySettingRepository settings;
    @MockitoSpyBean CalendarEventRepository events;
    @MockitoSpyBean ScheduleCrawlRunRepository runs;
    @Autowired UserRepository users;
    @Autowired PlatformTransactionManager manager;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired org.springframework.context.ApplicationContext context;
    @MockitoBean HknuAcademicCalendarCrawler crawler;
    TransactionTemplate tx;
    ExecutorService workers;
    Long userId, otherId, adminId, schoolId, dormitoryCategoryId, personalId;
    final Set<Long> ownedRuns = ConcurrentHashMap.newKeySet();
    final LocalDate day = LocalDate.of(2026, 10, 16);

    @BeforeEach void setup() {
        tx = new TransactionTemplate(manager);
        workers = Executors.newFixedThreadPool(2);
        doAnswer(call -> {
            ScheduleCrawlRun saved = (ScheduleCrawlRun) mockingDetails(runs)
                    .getMockCreationSettings().getDefaultAnswer().answer(call);
            ownedRuns.add(saved.getCrawlRunId());
            return saved;
        }).when(runs).save(any(ScheduleCrawlRun.class));
        tx.executeWithoutResult(s -> {
            userId = users.saveAndFlush(new User("test-hash", UUID.randomUUID()+"@hknu.ac.kr")).getUserId();
            otherId = users.saveAndFlush(new User("test-hash", UUID.randomUUID()+"@hknu.ac.kr")).getUserId();
            adminId = users.saveAndFlush(new User("test-hash", UUID.randomUUID()+"@hknu.ac.kr", UserRole.ADMIN)).getUserId();
            schoolId = categories.saveAndFlush(new CalendarCategory("SCHOOL", "학교", null, true, true)).getCategoryId();
            dormitoryCategoryId = categories.saveAndFlush(new CalendarCategory("DORMITORY", "기숙사", null, true, true)).getCategoryId();
        });
        personalId = calendar.createCategory(userId, category("personal")).id();
    }
    @AfterEach void cleanup() throws Exception {
        workers.shutdownNow(); assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS));
        tx.executeWithoutResult(s -> {
            jdbc.update("""
                    delete from CALENDAR_EVENT where owner_user_id in (?,?,?) or category_id in (?,?)
                    """, userId, otherId, adminId, schoolId, dormitoryCategoryId);
            jdbc.update("""
                    delete from CALENDAR_CATEGORY_SETTING where user_id in (?,?,?) or category_id in (?,?)
                    """, userId, otherId, adminId, schoolId, dormitoryCategoryId);
            jdbc.update("""
                    delete from CALENDAR_CATEGORY where owner_user_id in (?,?,?) or category_id in (?,?)
                    """, userId, otherId, adminId, schoolId, dormitoryCategoryId);
            ownedRuns.forEach(id -> jdbc.update("delete from SCHEDULE_CRAWL_RUN where crawl_run_id=?", id));
            users.deleteById(userId); users.deleteById(otherId); users.deleteById(adminId);
        });
    }
    CalendarCategoryCreateRequest category(String label) { return new CalendarCategoryCreateRequest(label, "#123456"); }
    CalendarCategorySettingRequest schoolSetting() { return new CalendarCategorySettingRequest(schoolId, "학교", "#123456", true); }
    CalendarEventCreateRequest event(Long categoryId, String start, String end) {
        return new CalendarEventCreateRequest("personal event", day, day, start, end, categoryId, "details");
    }
    List<CrawledAcademicEvent> collected() { return List.of(new CrawledAcademicEvent("academic", day, day)); }
    ScheduleCrawlRun latest() {
        return runs.findFirstByCategoryOrderByStartedAtDescCrawlRunIdDesc(ScheduleCrawlCategory.SCHOOL).orElseThrow();
    }
    void code(ErrorCode expected, org.junit.jupiter.api.function.Executable action) {
        assertEquals(expected, assertThrows(CustomException.class, action).getErrorCode());
    }
    void await(CountDownLatch latch) {
        try { assertTrue(latch.await(10, TimeUnit.SECONDS), "worker coordination timed out"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
    }
    Object outcome(Callable<?> action) {
        try { return action.call(); } catch (Exception ex) { return ex; }
    }
    /** Hold the first committed-to-be operation open; verify second call waits then rechecks. */
    Object serialize(Runnable firstAction, Callable<?> secondAction) throws Exception {
        var firstReady = new CountDownLatch(1); var release = new CountDownLatch(1); var secondStarted = new CountDownLatch(1);
        var first = workers.submit(() -> tx.executeWithoutResult(s -> { firstAction.run(); firstReady.countDown(); await(release); }));
        await(firstReady);
        var second = workers.submit(() -> { secondStarted.countDown(); return outcome(secondAction); });
        await(secondStarted);
        try { assertThrows(TimeoutException.class, () -> second.get(150, TimeUnit.MILLISECONDS)); }
        finally { release.countDown(); }
        first.get(15, TimeUnit.SECONDS); return second.get(15, TimeUnit.SECONDS);
    }

    @Test void recordsRunningBeforeHttpAndCompletedAfterCommit() {
        when(crawler.crawl()).thenAnswer(call -> {
            assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals(ScheduleCrawlStatus.RUNNING, latest().getStatus());
            var status = admins.getScheduleCrawlStatuses(adminId).stream().filter(s -> s.category() == ScheduleCrawlCategory.SCHOOL).findFirst().orElseThrow();
            assertEquals(ScheduleCrawlStatus.RUNNING, status.status()); assertNull(status.lastCompletedAt());
            return collected();
        });
        assertEquals(1, crawling.crawlAcademicCalendar());
        assertEquals(ScheduleCrawlStatus.COMPLETED, latest().getStatus());
        assertEquals(1, latest().getCollectedCount()); assertNotNull(latest().getCompletedAt()); assertEquals(1, events.count());
        var status = admins.getScheduleCrawlStatuses(adminId).stream().filter(s -> s.category() == ScheduleCrawlCategory.SCHOOL).findFirst().orElseThrow();
        assertEquals(1, status.recentCollectedCount()); assertEquals(1, status.totalCollectedCount()); assertNotNull(status.lastCompletedAt());
    }
    @Test void recordsExternalFailureWithoutLeakingOriginalMessage() {
        when(crawler.crawl()).thenThrow(new IllegalStateException("private upstream response"));
        assertThrows(IllegalStateException.class, () -> crawling.crawlAcademicCalendar());
        assertEquals(ScheduleCrawlStatus.FAILED, latest().getStatus()); assertEquals(0, latest().getCollectedCount());
        assertNotNull(latest().getCompletedAt()); assertFalse(latest().getErrorMessage().contains("private upstream"));
        assertEquals(0, events.count());
        var status = admins.getScheduleCrawlStatuses(adminId).stream().filter(s -> s.category() == ScheduleCrawlCategory.SCHOOL).findFirst().orElseThrow();
        assertNotNull(status.lastFailure()); assertNull(status.lastCompletedAt());
    }
    @Test void rollsBackPartialInsertsAndPreservesFailureHistory() {
        when(crawler.crawl()).thenReturn(List.of(new CrawledAcademicEvent("first", day, day), new CrawledAcademicEvent("fail", day, day)));
        doAnswer(call -> {
            CalendarEvent candidate = call.getArgument(0);
            if (candidate.getTitle().equals("fail")) throw new DataAccessResourceFailureException("private DB details");
            return mockingDetails(events).getMockCreationSettings().getDefaultAnswer().answer(call);
        }).when(events).save(any(CalendarEvent.class));
        assertThrows(DataAccessResourceFailureException.class, () -> crawling.crawlAcademicCalendar());
        assertEquals(0, events.count()); assertEquals(ScheduleCrawlStatus.FAILED, latest().getStatus());
        assertEquals(0, latest().getCollectedCount()); assertFalse(latest().getErrorMessage().contains("private DB"));
    }
    @Test void doesNotReportSuccessWhenStorageCommitFails() {
        when(crawler.crawl()).thenReturn(collected());
        doAnswer(call -> {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override public void beforeCommit(boolean readOnly) { throw new IllegalStateException("synthetic commit failure"); }
                });
            return mockingDetails(events).getMockCreationSettings().getDefaultAnswer().answer(call);
        }).when(events).save(any(CalendarEvent.class));
        assertThrows(IllegalStateException.class, () -> crawling.crawlAcademicCalendar());
        assertEquals(0, events.count()); assertEquals(ScheduleCrawlStatus.FAILED, latest().getStatus());
    }
    @Test void committedSuccessSurvivesLostCommitAcknowledgement() {
        when(crawler.crawl()).thenReturn(collected());
        doAnswer(call -> {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override public void afterCommit() { throw new IllegalStateException("synthetic lost acknowledgement"); }
                });
            return mockingDetails(events).getMockCreationSettings().getDefaultAnswer().answer(call);
        }).when(events).save(any(CalendarEvent.class));
        assertThrows(IllegalStateException.class, crawling::crawlAcademicCalendar);
        assertEquals(1, events.count()); assertEquals(ScheduleCrawlStatus.COMPLETED, latest().getStatus());
        assertEquals(1, latest().getCollectedCount());
    }
    @Test void unavailableFailureHistoryPreservesOriginalFailureAndRunningRecord() {
        var upstream = new IllegalStateException("original upstream failure");
        when(crawler.crawl()).thenThrow(upstream);
        doThrow(new DataAccessResourceFailureException("history unavailable")).when(runs).findByIdForUpdate(anyLong());
        assertSame(upstream, assertThrows(IllegalStateException.class, crawling::crawlAcademicCalendar));
        assertEquals(1, upstream.getSuppressed().length); assertEquals(ScheduleCrawlStatus.RUNNING, latest().getStatus());
        assertEquals(0, events.count());
    }
    @Test void unavailableStartHistoryPreventsHttpRequest() {
        doThrow(new DataAccessResourceFailureException("history unavailable")).when(runs).save(any(ScheduleCrawlRun.class));
        assertThrows(DataAccessResourceFailureException.class, crawling::crawlAcademicCalendar);
        verifyNoInteractions(crawler); assertEquals(0, runs.count()); assertEquals(0, events.count());
    }
    @Test void independentCrawlCommitSurvivesCallerRollback() {
        when(crawler.crawl()).thenReturn(collected());
        tx.executeWithoutResult(s -> { assertEquals(1, crawling.crawlAcademicCalendar()); s.setRollbackOnly(); });
        assertEquals(1, events.count()); assertEquals(ScheduleCrawlStatus.COMPLETED, latest().getStatus());
    }
    @Test void repeatedCrawlRecordsZeroNewEvents() {
        when(crawler.crawl()).thenReturn(collected());
        assertEquals(1, crawling.crawlAcademicCalendar()); assertEquals(0, crawling.crawlAcademicCalendar());
        assertEquals(1, events.count()); assertEquals(2, runs.count()); assertEquals(0, latest().getCollectedCount());
    }
    @Test void concurrentCrawlsHaveIndependentConsistentHistories() throws Exception {
        var ready = new CountDownLatch(2); var release = new CountDownLatch(1);
        when(crawler.crawl()).thenAnswer(call -> { ready.countDown(); await(release); return collected(); });
        var first = workers.submit(crawling::crawlAcademicCalendar); var second = workers.submit(crawling::crawlAcademicCalendar);
        try { await(ready); assertEquals(2, runs.count()); } finally { release.countDown(); }
        assertEquals(1, first.get(15, TimeUnit.SECONDS) + second.get(15, TimeUnit.SECONDS)); assertEquals(1, events.count());
        assertEquals(2, runs.count()); assertTrue(runs.findAll().stream().allMatch(r -> r.getStatus() == ScheduleCrawlStatus.COMPLETED));
        assertEquals(1, runs.findAll().stream().mapToInt(ScheduleCrawlRun::getCollectedCount).sum());
    }
    @Test void newerFailureDoesNotEraseLastSuccessAndUnfinishedRunIsNotAutomaticallyFailed() {
        when(crawler.crawl()).thenReturn(collected()); crawling.crawlAcademicCalendar();
        var completedAt = latest().getCompletedAt();
        when(crawler.crawl()).thenThrow(new IllegalStateException("network")); assertThrows(IllegalStateException.class, crawling::crawlAcademicCalendar);
        var status = admins.getScheduleCrawlStatuses(adminId).getFirst();
        assertEquals(ScheduleCrawlStatus.FAILED, status.status()); assertEquals(completedAt, status.lastCompletedAt());
        assertNotNull(status.lastFailure());
        ownedRuns.add(runs.saveAndFlush(new ScheduleCrawlRun(ScheduleCrawlCategory.SCHOOL,
                java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Seoul")).plusSeconds(1))).getCrawlRunId());
        assertEquals(ScheduleCrawlStatus.RUNNING, admins.getScheduleCrawlStatuses(adminId).getFirst().status());
    }
    @Test void sameUserCannotCreateDuplicateCategoriesConcurrently() throws Exception {
        Object result = serialize(() -> calendar.createCategory(userId, category("duplicate")), () -> calendar.createCategory(userId, category("duplicate")));
        assertInstanceOf(CustomException.class, result); assertEquals(ErrorCode.VALIDATION_ERROR, ((CustomException)result).getErrorCode());
        assertEquals(1, calendar.getCategories(userId).stream().filter(c -> c.label().equals("duplicate")).count());
    }
    @Test void firstSettingsSaveWaitsAndUpdatesExistingRow() throws Exception {
        Object result = serialize(() -> calendar.saveCategorySettings(userId, List.of(schoolSetting())),
                () -> calendar.saveCategorySettings(userId, List.of(new CalendarCategorySettingRequest(schoolId, "학교", "#654321", false))));
        assertInstanceOf(List.class, result);
        var saved = settings.findByUser_UserIdAndCategory_CategoryId(userId, schoolId).orElseThrow();
        assertEquals("#654321", saved.getColor()); assertFalse(saved.isEnabled());
        assertEquals(2, settings.findAllByUser_UserId(userId).size());
    }
    @Test void renameRechecksDuplicateNameAfterUserLock() throws Exception {
        var second = calendar.createCategory(userId, category("second"));
        Object result = serialize(() -> calendar.updateCategory(userId, personalId, new CalendarCategoryUpdateRequest("target", null, null)),
                () -> calendar.updateCategory(userId, second.id(), new CalendarCategoryUpdateRequest("target", null, null)));
        assertInstanceOf(CustomException.class, result); assertEquals(ErrorCode.VALIDATION_ERROR, ((CustomException)result).getErrorCode());
    }
    @Test void differentUsersDoNotBlockEachOther() throws Exception {
        var ready = new CountDownLatch(1); var release = new CountDownLatch(1);
        var first = workers.submit(() -> tx.executeWithoutResult(s -> { calendar.createCategory(userId, category("shared")); ready.countDown(); await(release); }));
        try {
            await(ready);
            var second = workers.submit(() -> calendar.createCategory(otherId, category("shared")));
            assertEquals("shared", second.get(5, TimeUnit.SECONDS).label());
        } finally { release.countDown(); }
        first.get(15, TimeUnit.SECONDS);
    }
    @Test void batchRenameAndCreateUseTheSameUserLock() throws Exception {
        Object result = serialize(() -> calendar.saveCategorySettings(userId,
                List.of(new CalendarCategorySettingRequest(personalId, "target", "#123456", true))),
                () -> calendar.createCategory(userId, category("target")));
        assertInstanceOf(CustomException.class, result); assertEquals(ErrorCode.VALIDATION_ERROR, ((CustomException)result).getErrorCode());
    }
    @Test void categoryDeletionWaitsForEventCreationAndRechecksUsage() throws Exception {
        Object result = serialize(() -> calendar.createEvent(userId, event(personalId, "10:00", "11:00")),
                () -> { calendar.deleteCategory(userId, personalId); return null; });
        assertInstanceOf(CustomException.class, result); assertEquals(ErrorCode.INVALID_REQUEST, ((CustomException)result).getErrorCode());
        assertTrue(categories.existsById(personalId)); assertEquals(1, events.count());
    }
    @Test void visibilityOwnershipAndSystemProtectionRemainIntact() {
        when(crawler.crawl()).thenReturn(collected()); crawling.crawlAcademicCalendar();
        var mine = calendar.createEvent(userId, event(personalId, "10:00", "11:00"));
        var other = calendar.createCategory(otherId, category("other")); calendar.createEvent(otherId, event(other.id(), null, null));
        var range = new CalendarEventListRequest(day, day, null);
        assertEquals(1, calendar.getEvents(null, range).size()); assertEquals(2, calendar.getEvents(userId, range).size());
        code(ErrorCode.FORBIDDEN, () -> calendar.deleteEvent(otherId, mine.id()));
        code(ErrorCode.FORBIDDEN, () -> calendar.createEvent(userId, event(other.id(), "10:00", "11:00")));
        code(ErrorCode.FORBIDDEN, () -> calendar.createEvent(userId, event(schoolId, "10:00", "11:00")));
        code(ErrorCode.FORBIDDEN, () -> calendar.updateCategory(otherId, personalId, new CalendarCategoryUpdateRequest("stolen", null, null)));
        code(ErrorCode.FORBIDDEN, () -> calendar.deleteCategory(userId, schoolId));
        code(ErrorCode.INVALID_REQUEST, () -> calendar.deleteCategory(userId, personalId));
        code(ErrorCode.FORBIDDEN, () -> admins.getScheduleCrawlStatuses(userId));
    }
    @Test void validationPatchMergeAndBatchRollbackRemainIntact() {
        code(ErrorCode.VALIDATION_ERROR, () -> calendar.createEvent(userId, event(personalId, "24:00", "11:00")));
        code(ErrorCode.VALIDATION_ERROR, () -> calendar.createEvent(userId, event(personalId, "11:00", "10:00")));
        var mine = calendar.createEvent(userId, event(personalId, "10:00", null));
        code(ErrorCode.VALIDATION_ERROR, () -> calendar.updateEvent(userId, mine.id(), new CalendarEventUpdateRequest(null, null, day.minusDays(1), null, null, null, null)));
        assertEquals(day.plusDays(1), calendar.updateEvent(userId, mine.id(), new CalendarEventUpdateRequest(null, null, day.plusDays(1), null, null, null, null)).endDate());
        code(ErrorCode.VALIDATION_ERROR, () -> calendar.saveCategorySettings(userId, Arrays.asList(new CalendarCategorySettingRequest(personalId, "changed", "#654321", true), null)));
        assertEquals("personal", calendar.getCategories(userId).stream().filter(c -> c.id().equals(personalId)).findFirst().orElseThrow().label());
        calendar.deleteEvent(userId, mine.id()); calendar.deleteCategory(userId, personalId);
        assertFalse(categories.existsById(personalId));
    }
    @Test void sharedAccountLockRechecksActiveStatus() {
        tx.executeWithoutResult(s -> users.findByIdForAccountUpdate(userId).orElseThrow().changeStatus(UserStatus.SUSPENDED));
        code(ErrorCode.UNAUTHORIZED, () -> calendar.createCategory(userId, category("blocked")));
        code(ErrorCode.UNAUTHORIZED, () -> calendar.saveCategorySettings(userId, List.of(schoolSetting())));
    }
    @Test void testProfileDoesNotRegisterTheAutomaticCrawler() {
        assertTrue(context.getBeansOfType(com.kkirikkiri.calendar.scheduler.CalendarCrawlingScheduler.class).isEmpty());
        verifyNoInteractions(crawler);
    }

    // Raw JDBC comparisons supplement the separate real-MySQL timezone matrix.
    @Test void historyRawDatetimeAndAdminPreserveTheMidnightBoundary() {
        var start = LocalDateTime.of(2026, 10, 16, 23, 59, 59);
        Long id = tx.execute(s -> runs.saveAndFlush(new ScheduleCrawlRun(ScheduleCrawlCategory.SCHOOL, start)).getCrawlRunId());
        ownedRuns.add(id);
        assertEquals(start, jdbc.queryForObject("select started_at from schedule_crawl_run where crawl_run_id=?", LocalDateTime.class, id));
        assertNull(runs.findById(id).orElseThrow().getCompletedAt());
        tx.executeWithoutResult(s -> runs.findById(id).orElseThrow().complete(0, start.plusSeconds(1)));
        var status = admins.getScheduleCrawlStatuses(adminId).stream().filter(s -> s.category() == ScheduleCrawlCategory.SCHOOL).findFirst().orElseThrow();
        assertEquals(start, status.lastRunAt()); assertEquals(start.plusSeconds(1), status.lastCompletedAt());
        assertEquals(status.lastCompletedAt(), jdbc.queryForObject("select completed_at from schedule_crawl_run where crawl_run_id=?", LocalDateTime.class, id));
    }

    @Test void scheduleRawLocalDateAndTimePreserveMidnightAndNulls() {
        var saved = calendar.createEvent(userId, new CalendarEventCreateRequest("midnight", day, day.plusDays(1), "23:59", "00:00", personalId, null));
        assertEquals(day, jdbc.queryForObject("select start_date from calendar_event where event_id=?", LocalDate.class, saved.id()));
        assertEquals(day.plusDays(1), jdbc.queryForObject("select end_date from calendar_event where event_id=?", LocalDate.class, saved.id()));
        assertEquals(LocalTime.of(23, 59), jdbc.queryForObject("select start_time from calendar_event where event_id=?", LocalTime.class, saved.id()));
        assertEquals(LocalTime.MIDNIGHT, jdbc.queryForObject("select end_time from calendar_event where event_id=?", LocalTime.class, saved.id()));
        for (String[] times : new String[][] { { "10:00", null }, { null, "11:00" }, { null, null } }) {
            calendar.createEvent(userId, event(personalId, times[0], times[1]));
        }
        var listed = calendar.getEvents(userId, new CalendarEventListRequest(day, day.plusDays(1), null));
        assertEquals(4, listed.size());
        assertEquals(2, listed.stream().filter(e -> e.startTime() == null).count());
        assertEquals(2, listed.stream().filter(e -> e.endTime() == null).count());
    }

    @Test void generatedCategoryAndCollectionDatetimesUseSeoulWallTime() {
        var before = LocalDateTime.now(ZoneId.of("Asia/Seoul")).minusSeconds(1);
        var created = calendar.createCategory(userId, category("temporal"));
        var category = categories.findById(created.id()).orElseThrow();
        assertFalse(category.getCreatedAt().isBefore(before));
        assertEquals(category.getCreatedAt(), jdbc.queryForObject("select created_at from calendar_category where category_id=?", LocalDateTime.class, created.id()));
        when(crawler.crawl()).thenReturn(collected()); crawling.crawlAcademicCalendar();
        var collected = events.findAll().getFirst();
        assertFalse(collected.getCollectedAt().isBefore(before));
        assertEquals(collected.getCollectedAt(), jdbc.queryForObject("select collected_at from calendar_event where event_id=?", LocalDateTime.class, collected.getEventId()));
        assertFalse(collected.getCollectedAt().isAfter(LocalDateTime.now(ZoneId.of("Asia/Seoul")).plusSeconds(1)));
    }
}
