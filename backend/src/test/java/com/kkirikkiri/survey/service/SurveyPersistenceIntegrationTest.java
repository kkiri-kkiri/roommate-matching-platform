package com.kkirikkiri.survey.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static com.kkirikkiri.survey.SurveyTestData.*;

import java.util.*;
import java.util.concurrent.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.*;
import tools.jackson.databind.json.JsonMapper;
import com.kkirikkiri.admin.dto.AdminMemberStatusUpdateRequest;
import com.kkirikkiri.admin.service.AdminService;
import com.kkirikkiri.global.exception.*;
import com.kkirikkiri.survey.dto.*;
import com.kkirikkiri.survey.repository.*;
import com.kkirikkiri.user.entity.*;
import com.kkirikkiri.user.repository.*;

/** No test-level transaction: setup, workers and verification each commit independently. */
@SpringBootTest
@ActiveProfiles("test")
class SurveyPersistenceIntegrationTest {
    @Autowired ProfileSurveyService profileService;
    @Autowired RoommatePreferenceService preferenceService;
    @Autowired AdminService admins;
    @Autowired UserRepository users;
    @Autowired UserProfileRepository profiles;
    @Autowired ProfileSurveyRepository surveys;
    @Autowired RoommatePreferenceRepository preferences;
    @MockitoSpyBean ProfileSurveyOptionRepository profileOptions;
    @MockitoSpyBean PreferenceCriterionRepository criteria;
    // Exercise the actual AFTER_COMMIT listener; capture submissions without background DB writes.
    @MockitoBean(name = "matchingRefreshExecutor") Executor matchingExecutor;
    @Autowired PlatformTransactionManager manager;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    TransactionTemplate tx;
    ExecutorService workers;
    Long userId, adminId, dormId, facultyId;
    final JsonMapper json = JsonMapper.builder().build();
    final Set<Long> sessions = ConcurrentHashMap.newKeySet();

    @BeforeEach void setup() {
        tx = new TransactionTemplate(manager); tx.setTimeout(15);
        workers = Executors.newFixedThreadPool(2);
        tx.executeWithoutResult(s -> {
            userId = users.saveAndFlush(new User("test-hash", UUID.randomUUID()+"@hknu.ac.kr")).getUserId();
            adminId = users.saveAndFlush(new User("test-hash", UUID.randomUUID()+"@hknu.ac.kr", UserRole.ADMIN)).getUserId();
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            var dorm = new Dormitory("sv-"+suffix, "survey-"+suffix, true); em.persist(dorm); dormId = dorm.getDormitoryId();
            var faculty = new Faculty("survey-"+suffix, true); em.persist(faculty); facultyId = faculty.getFacultyId();
            profiles.saveAndFlush(new UserProfile(users.getReferenceById(userId), "sv-"+suffix, "MALE", dorm,
                    faculty, null, (short)2003, "22", "3학년", "INTJ"));
        });
    }
    @AfterEach void cleanup() throws Exception {
        workers.shutdownNow(); assertTrue(workers.awaitTermination(20, TimeUnit.SECONDS));
        reset(profileOptions, criteria);
        // Only rows owned by this test. No global table resets.
        tx.executeWithoutResult(s -> {
            jdbc.update("delete from PROFILE_SURVEY_OPTION where profile_survey_id in (select profile_survey_id from PROFILE_SURVEY where user_id=?)", userId);
            jdbc.update("delete from ROOMMATE_PREFERENCE_OPTION where preference_id in (select preference_id from ROOMMATE_PREFERENCE where user_id=?)", userId);
            jdbc.update("delete from PREFERENCE_CRITERION where preference_id in (select preference_id from ROOMMATE_PREFERENCE where user_id=?)", userId);
            jdbc.update("delete from PROFILE_SURVEY where user_id=?", userId);
            jdbc.update("delete from ROOMMATE_PREFERENCE where user_id=?", userId);
            profiles.findByUser_UserId(userId).ifPresent(profiles::delete); profiles.flush();
            users.deleteById(userId); users.deleteById(adminId); users.flush();
            jdbc.update("delete from DORMITORY where dormitory_id=?", dormId);
            jdbc.update("delete from FACULTY where faculty_id=?", facultyId);
        });
    }
    void save(boolean preference, boolean second) {
        if (preference) preferenceService.saveOrUpdateMyRoommatePreference(userId, preference(second));
        else profileService.saveOrUpdateMyProfileSurvey(userId, profile(second));
    }
    void session() { sessions.add(jdbc.queryForObject("select session_id()", Long.class)); }
    void await(CountDownLatch latch) {
        try { assertTrue(latch.await(10, TimeUnit.SECONDS), "worker coordination timed out"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
    }
    void deactivate(UserStatus status) {
        admins.updateMemberStatus(adminId, userId, new AdminMemberStatusUpdateRequest(status.name()));
    }
    List<List<Map<String, Object>>> snapshot() {
        return tx.execute(s -> List.of(
                jdbc.queryForList("select * from USER_PROFILE where user_id=?", userId),
                jdbc.queryForList("select * from PROFILE_SURVEY where user_id=?", userId),
                jdbc.queryForList("select o.* from PROFILE_SURVEY_OPTION o join PROFILE_SURVEY p on p.profile_survey_id=o.profile_survey_id where p.user_id=? order by o.profile_survey_option_id", userId),
                jdbc.queryForList("select * from ROOMMATE_PREFERENCE where user_id=?", userId),
                jdbc.queryForList("select o.* from ROOMMATE_PREFERENCE_OPTION o join ROOMMATE_PREFERENCE p on p.preference_id=o.preference_id where p.user_id=? order by o.preference_option_id", userId),
                jdbc.queryForList("select c.* from PREFERENCE_CRITERION c join ROOMMATE_PREFERENCE p on p.preference_id=c.preference_id where p.user_id=? order by c.preference_criterion_id", userId)));
    }
    void assertSaved(boolean preference, boolean second) {
        tx.executeWithoutResult(s -> {
            assertEquals(1, jdbc.queryForObject("select count(*) from "+(preference ? "ROOMMATE_PREFERENCE" : "PROFILE_SURVEY")+" where user_id=?", Integer.class, userId));
            if (preference) {
                var result = preferenceService.getMyRoommatePreference(userId); assertTrue(result.completed());
                assertEquals(json.convertValue(preference(second), Map.class), json.convertValue(result.answers(), Map.class));
                var id = preferences.findByUser_UserId(userId).orElseThrow().getPreferenceId();
                assertEquals(second ? 6 : 7, jdbc.queryForObject("select count(*) from ROOMMATE_PREFERENCE_OPTION where preference_id=?", Integer.class, id));
                assertEquals(18, jdbc.queryForObject("select count(*) from PREFERENCE_CRITERION where preference_id=?", Integer.class, id));
                assertEquals(18, jdbc.queryForObject("select count(distinct criterion_code) from PREFERENCE_CRITERION where preference_id=?", Integer.class, id));
                assertEquals(18, jdbc.queryForObject("select count(*) from PREFERENCE_CRITERION where preference_id=? and weight=?", Integer.class, id, second ? 5 : 2));
            } else {
                var result = profileService.getMyProfileSurvey(userId); assertTrue(result.completed());
                Map<String, Object> actual = new LinkedHashMap<>(json.convertValue(result.answers(), Map.class));
                actual.put("introduction", result.introduction());
                assertEquals(json.convertValue(profile(second), Map.class), actual);
                var id = surveys.findByUser_UserId(userId).orElseThrow().getProfileSurveyId();
                assertEquals(second ? 3 : 4, jdbc.queryForObject("select count(*) from PROFILE_SURVEY_OPTION where profile_survey_id=?", Integer.class, id));
            }
        });
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void firstSaveCommitsParentAndChildrenAndRepeatedUpdatesReplaceEverything(boolean preference) {
        save(preference, false); assertSaved(preference, false);
        var before = snapshot();
        save(preference, true); assertSaved(preference, true);
        var after = snapshot();
        int parent = preference ? 3 : 1; String key = preference ? "PREFERENCE_ID" : "PROFILE_SURVEY_ID";
        assertEquals(before.get(parent).getFirst().get(key), after.get(parent).getFirst().get(key));
        assertTrue(Collections.disjoint(before.get(parent+1), after.get(parent+1)));
        if (preference) assertTrue(Collections.disjoint(before.get(5), after.get(5)));
        save(preference, true); assertSaved(preference, true);
        verify(matchingExecutor, times(preference ? 3 : 2)).execute(any());
    }
    @Test void introductionNullPreservesAndBlankClearsWithNoExtraMatchingRefresh() {
        save(false, false);
        var legacy = change(profile(false), "introduction", null, ProfileSurveyRequest.class);
        assertEquals("처음 소개", profileService.saveOrUpdateMyProfileSurvey(userId, legacy).introduction());
        for (String text : List.of("변경 소개", "", " \n\t", "\u00a0\u3000\ufeff")) {
            profileService.saveOrUpdateMyProfileSurvey(userId, change(profile(false), "introduction", text, ProfileSurveyRequest.class));
            assertEquals(text.equals("변경 소개") ? text : "", tx.execute(s -> profiles.findByUser_UserId(userId).orElseThrow().getIntroduction()));
        }
        verify(matchingExecutor, times(1)).execute(any());
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void simultaneousFirstSavesUseDistinctConnectionsAndCommitOneCompleteRequest(boolean preference) throws Exception {
        var ready = new CountDownLatch(2); var start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (boolean second : List.of(false, true)) futures.add(workers.submit(() -> tx.executeWithoutResult(s -> {
            session(); ready.countDown(); await(start); save(preference, second);
        })));
        await(ready); start.countDown();
        for (var future : futures) future.get(20, TimeUnit.SECONDS);
        assertEquals(2, sessions.size());
        boolean second = preference ? preferenceService.getMyRoommatePreference(userId).answers().department().equals(preference(true).department())
                : profileService.getMyProfileSurvey(userId).answers().sleepTime().equals(profile(true).sleepTime());
        assertSaved(preference, second);
        verify(matchingExecutor, times(2)).execute(any());
    }
    static java.util.stream.Stream<Arguments> failureStages() {
        return java.util.stream.Stream.of(Arguments.of(false, false), Arguments.of(true, false),
                Arguments.of(false, true), Arguments.of(true, true));
    }
    @ParameterizedTest @MethodSource("failureStages")
    void lateStorageOrCommitFailureRollsBackAllRowsAndSuppressesAfterCommitRefresh(boolean preference, boolean commitFailure) {
        save(preference, false); var before = snapshot(); clearInvocations(matchingExecutor);
        if (!commitFailure) {
            var repository = preference ? criteria : profileOptions;
            org.mockito.stubbing.Answer<Object> failAfterRealInsert = call -> {
                Object value = mockingDetails(repository).getMockCreationSettings().getDefaultAnswer().answer(call);
                em.flush(); // Existing deletes, new inserts, parent and introduction have actually reached H2.
                throw new IllegalStateException("test-only failure after real child inserts");
            };
            if (preference) doAnswer(failAfterRealInsert).when(criteria).saveAll(anyList());
            else doAnswer(failAfterRealInsert).when(profileOptions).saveAll(anyList());
        }
        assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(s -> {
            save(preference, true);
            if (commitFailure) TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void beforeCommit(boolean readOnly) { em.flush(); throw new IllegalStateException("test-only commit failure"); }
            });
        }));
        assertEquals(before, snapshot()); assertSaved(preference, false);
        verifyNoInteractions(matchingExecutor);
    }
    static java.util.stream.Stream<Arguments> states() {
        return java.util.stream.Stream.of(false, true).flatMap(p -> java.util.stream.Stream.of(UserStatus.SUSPENDED, UserStatus.WITHDRAWN)
                .map(status -> Arguments.of(p, status)));
    }
    @ParameterizedTest @MethodSource("states")
    void statusCommitFirstRefreshesEvenAnAlreadyManagedActiveUserAndRejectsWaitingSave(boolean preference, UserStatus status) throws Exception {
        save(preference, false); var before = snapshot(); clearInvocations(matchingExecutor);
        var staleLoaded = new CountDownLatch(1); var statusLocked = new CountDownLatch(1);
        var tryingSave = new CountDownLatch(1); var releaseStatus = new CountDownLatch(1);
        var survey = workers.submit(() -> assertThrows(CustomException.class, () -> tx.executeWithoutResult(s -> {
            session(); assertEquals(UserStatus.ACTIVE, users.findById(userId).orElseThrow().getStatus());
            staleLoaded.countDown(); await(statusLocked); tryingSave.countDown(); save(preference, true);
        })));
        await(staleLoaded);
        var account = workers.submit(() -> tx.executeWithoutResult(s -> {
            session(); deactivate(status); em.flush(); statusLocked.countDown(); await(releaseStatus);
        }));
        await(statusLocked); await(tryingSave);
        try { assertThrows(TimeoutException.class, () -> survey.get(200, TimeUnit.MILLISECONDS)); }
        finally { releaseStatus.countDown(); }
        account.get(20, TimeUnit.SECONDS);
        assertEquals(ErrorCode.UNAUTHORIZED, survey.get(20, TimeUnit.SECONDS).getErrorCode());
        assertEquals(2, sessions.size()); assertEquals(status, users.findById(userId).orElseThrow().getStatus());
        assertEquals(before, snapshot()); verifyNoInteractions(matchingExecutor);
    }
    @ParameterizedTest @MethodSource("states")
    void surveyCommitFirstMakesAccountChangeWaitAndBothCommitInOrder(boolean preference, UserStatus status) throws Exception {
        var saved = new CountDownLatch(1); var releaseSurvey = new CountDownLatch(1); var tryingStatus = new CountDownLatch(1);
        var survey = workers.submit(() -> tx.executeWithoutResult(s -> {
            session(); save(preference, true); em.flush(); saved.countDown(); await(releaseSurvey);
        }));
        await(saved);
        var account = workers.submit(() -> tx.executeWithoutResult(s -> {
            session(); tryingStatus.countDown(); deactivate(status);
        }));
        await(tryingStatus);
        try {
            assertThrows(TimeoutException.class, () -> account.get(200, TimeUnit.MILLISECONDS));
            verifyNoInteractions(matchingExecutor); // Event is still waiting for the survey commit.
        } finally { releaseSurvey.countDown(); }
        survey.get(20, TimeUnit.SECONDS); account.get(20, TimeUnit.SECONDS);
        assertEquals(2, sessions.size()); assertSaved(preference, true);
        assertEquals(status, users.findById(userId).orElseThrow().getStatus());
        verify(matchingExecutor, times(1)).execute(any());
    }
}
