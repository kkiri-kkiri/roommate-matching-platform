package com.kkirikkiri.matching.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static com.kkirikkiri.survey.SurveyTestData.*;
import java.util.*;
import java.util.concurrent.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import com.kkirikkiri.matching.dto.*;
import com.kkirikkiri.matching.entity.*;
import com.kkirikkiri.matching.repository.*;
import com.kkirikkiri.survey.dto.ProfileSurveyRequest;
import com.kkirikkiri.survey.service.ProfileSurveyService;
import com.kkirikkiri.user.entity.*;
import com.kkirikkiri.user.repository.*;

@SpringBootTest
@ActiveProfiles("test")
class MatchingVisibilityIntegrationTest {
    @Autowired RoommateMatchingService service;
    @Autowired ProfileSurveyService surveyService;
    @Autowired UserRepository users;
    @Autowired UserProfileRepository profiles;
    @Autowired RoommateMatchRepository matches;
    @Autowired ProfileLikeRepository likes;
    @Autowired PlatformTransactionManager manager;
    @Autowired EntityManager em;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @MockitoBean MatchingRefreshService refresh;
    @MockitoBean(name = "matchingRefreshExecutor") Executor executor;
    final List<Long> owned = new ArrayList<>();
    TransactionTemplate tx;
    Long requester, target, other, dormId, facultyId;

    @BeforeEach void setup() {
        tx = new TransactionTemplate(manager); tx.setTimeout(15);
        tx.executeWithoutResult(s -> {
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            var dorm = new Dormitory("mv-"+suffix, "mv-"+suffix, true); em.persist(dorm); dormId=dorm.getDormitoryId();
            var faculty = new Faculty("mv-"+suffix, true); em.persist(faculty); facultyId=faculty.getFacultyId();
        });
        requester = user(false); target = user(true); other = user(true);
        tx.executeWithoutResult(s -> {
            likes.saveAndFlush(new ProfileLike(users.getReferenceById(requester), users.getReferenceById(other)));
            likes.saveAndFlush(new ProfileLike(users.getReferenceById(requester), users.getReferenceById(target)));
        });
    }
    Long user(boolean completed) {
        Long id = tx.execute(s -> {
            var u = users.saveAndFlush(new User("test-hash", UUID.randomUUID()+"@hknu.ac.kr")); owned.add(u.getUserId());
            profiles.saveAndFlush(new UserProfile(u, "mv"+u.getUserId(), "MALE", em.getReference(Dormitory.class,dormId),
                    em.getReference(Faculty.class,facultyId), null, (short)2003, "22", "3학년", "INTJ"));
            return u.getUserId();
        });
        if (completed) surveyService.saveOrUpdateMyProfileSurvey(id, profile(false));
        return id;
    }
    @AfterEach void cleanup() {
        reset(refresh);
        tx.executeWithoutResult(s -> {
            for (Long id : owned) {
                jdbc.update("delete from PROFILE_LIKE where user_id=? or target_user_id=?", id,id);
                jdbc.update("delete from ROOMMATE_MATCH where requester_user_id=? or target_user_id=?", id,id);
                jdbc.update("delete from PROFILE_SURVEY_OPTION where profile_survey_id in (select profile_survey_id from PROFILE_SURVEY where user_id=?)",id);
                jdbc.update("delete from PROFILE_SURVEY where user_id=?",id);
                jdbc.update("delete from USER_PROFILE where user_id=?",id);
                jdbc.update("delete from `USER` where user_id=?",id);
            }
            jdbc.update("delete from DORMITORY where dormitory_id=?",dormId);
            jdbc.update("delete from FACULTY where faculty_id=?",facultyId);
        });
    }
    void await(CountDownLatch latch) {
        try { assertTrue(latch.await(10,TimeUnit.SECONDS),"coordination timeout"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
    }
    void status(Long id, UserStatus status) {
        tx.executeWithoutResult(s -> users.findByIdForAccountUpdate(id).orElseThrow().changeStatus(status));
    }

    @ParameterizedTest @CsvSource({"false,SUSPENDED","false,WITHDRAWN","true,SUSPENDED","true,WITHDRAWN"})
    void deactivationBetweenIdPageAndAssemblyRequeriesContentAndCount(boolean liked, UserStatus state) throws Exception {
        var ready = new CountDownLatch(1); var proceed = new CountDownLatch(1);
        doAnswer(call -> {
            assertTrue(((Collection<?>)call.getArgument(1)).contains(target));
            ready.countDown(); await(proceed); return null;
        }).when(refresh).refreshTargets(eq(requester), anyList());
        try (var pool = Executors.newSingleThreadExecutor()) {
            var response = pool.submit(() -> liked ? service.getLikedRoommates(requester,PageRequest.of(0,1))
                    : service.getRoommates(requester,null,PageRequest.of(0,1)));
            try { await(ready); status(target,state); }
            finally { proceed.countDown(); }
            var page = response.get(15,TimeUnit.SECONDS);
            assertEquals(List.of(other),page.content().stream().map(RoommateProfileResponse::id).toList());
            assertEquals(1,page.totalElements()); assertEquals(1,page.totalPages());
            assertEquals(0,page.page()); assertTrue(page.first()); assertTrue(page.last());
        }
    }

    @ParameterizedTest @org.junit.jupiter.params.provider.EnumSource(value=UserStatus.class,
            names={"SUSPENDED","WITHDRAWN"})
    void recommendationRechecksStatusAfterScorePreparation(UserStatus state) throws Exception {
        score(target,100); score(other,90);
        var ready=new CountDownLatch(1); var proceed=new CountDownLatch(1);
        doAnswer(call -> { ready.countDown(); await(proceed); return null; })
                .when(refresh).refreshOutgoing(requester);
        try(var pool=Executors.newSingleThreadExecutor()) {
            var response=pool.submit(()->service.getRecommendedRoommates(requester,PageRequest.of(0,9)));
            try { await(ready); status(target,state); }
            finally { proceed.countDown(); }
            var page=response.get(15,TimeUnit.SECONDS);
            assertEquals(List.of(other),page.content().stream().map(RoommateProfileResponse::id).toList());
            assertEquals(1,page.totalElements()); assertEquals(1,page.totalPages());
            assertTrue(page.first()); assertTrue(page.last());
        }
    }

    @Test void disappearingLastPageHasConsistentEmptyMetadata() {
        doAnswer(call -> { status(other,UserStatus.SUSPENDED); return null; })
                .when(refresh).refreshTargets(eq(requester),anyList());
        var result = service.getRoommates(requester,null,PageRequest.of(1,1));
        assertTrue(result.content().isEmpty()); assertEquals(1,result.totalElements());
        assertEquals(1,result.totalPages()); assertEquals(1,result.page());
        assertFalse(result.first()); assertTrue(result.last());
    }

    RoommateFilterRequest filter(Map<String,Object> values) {
        return JsonMapper.builder().build().convertValue(values,RoommateFilterRequest.class);
    }
    void score(Long id,int score) {
        tx.executeWithoutResult(s -> matches.saveAndFlush(new RoommateMatch(users.getReferenceById(requester),
                users.getReferenceById(id),score,null)));
    }
    @Test void recommendationFiltersOnlyTheOriginalTopNineAndKeepsTieOrder() {
        List<Long> ranked = new ArrayList<>(List.of(target,other));
        for(int i=2;i<12;i++) ranked.add(user(true));
        for(int i=0;i<ranked.size();i++) score(ranked.get(i),i<2?100:100-i);
        for(int i:List.of(2,11)) surveyService.saveOrUpdateMyProfileSurvey(ranked.get(i),
                change(profile(false),"smoking","전자담배",ProfileSurveyRequest.class));
        var top = service.getRecommendedRoommates(requester,PageRequest.of(0,9));
        assertEquals(ranked.subList(0,9),top.content().stream().map(RoommateProfileResponse::id).toList());
        var one = service.getRecommendedRoommates(requester,filter(Map.of("smoking",List.of("전자담배"))),PageRequest.of(0,9));
        assertEquals(List.of(ranked.get(2)),one.content().stream().map(RoommateProfileResponse::id).toList());
        assertEquals(1,one.totalElements()); assertEquals(1,one.totalPages());
        var multiple = service.getRecommendedRoommates(requester,filter(Map.of("smoking",List.of("전자담배"),
                "mealPlan",List.of("석식"),"dormStayDays",List.of("금"))),PageRequest.of(0,9));
        assertEquals(one.content(),multiple.content());
        var empty = service.getRecommendedRoommates(requester,filter(Map.of("smoking",List.of("전자담배"),
                "meal",List.of("각자 먹고 싶음"))),PageRequest.of(0,9));
        assertTrue(empty.content().isEmpty()); assertEquals(0,empty.totalElements()); assertEquals(0,empty.totalPages());
        status(ranked.get(2),UserStatus.SUSPENDED);
        assertTrue(service.getRecommendedRoommates(requester,filter(Map.of("smoking",List.of("전자담배"))),PageRequest.of(0,9)).content().isEmpty());
        // Unfiltered paging remains compatible.
        assertEquals(2,service.getRecommendedRoommates(requester,PageRequest.of(1,9)).content().size());
    }
    @Test void noRecommendationCandidatesReturnsEmptyPage() {
        score(target,59); status(other,UserStatus.WITHDRAWN);
        var result=service.getRecommendedRoommates(requester,filter(Map.of("smoking",List.of("비흡연"))),PageRequest.of(2,9));
        assertTrue(result.content().isEmpty()); assertEquals(0,result.totalElements());
        assertEquals(2,result.page()); assertFalse(result.first()); assertTrue(result.last());
    }
    @Test void manyCandidatesStillLoadOnlyTheRankedNineForFilterAndDto() {
        score(target,100); score(other,99);
        for(int i=0;i<110;i++) score(user(true),80);
        var statistics=em.getEntityManagerFactory().unwrap(org.hibernate.SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true); statistics.clear();
        try {
            var result=service.getRecommendedRoommates(requester,filter(Map.of("smoking",List.of("비흡연"))),PageRequest.of(0,9));
            assertEquals(9,result.content().size()); assertTrue(statistics.getEntityLoadCount()<100);
            assertTrue(statistics.getQueryExecutionCount()<15);
        } finally { statistics.setStatisticsEnabled(false); }
        verify(refresh,times(1)).refreshOutgoing(requester);
        verify(refresh,never()).refreshTargets(anyLong(),anyList());
    }
}
