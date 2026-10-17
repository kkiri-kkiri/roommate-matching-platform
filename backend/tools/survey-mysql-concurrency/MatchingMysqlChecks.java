import static com.kkirikkiri.survey.SurveyTestData.*;
import com.kkirikkiri.matching.service.*;
import com.kkirikkiri.matching.repository.*;
import com.kkirikkiri.matching.dto.*;
import com.kkirikkiri.matching.entity.*;
import com.kkirikkiri.survey.dto.ProfileSurveyRequest;
import com.kkirikkiri.survey.repository.*;
import com.kkirikkiri.user.entity.*;
import com.kkirikkiri.user.repository.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import org.springframework.context.annotation.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.PlatformTransactionManager;

/** Runs only after the runner verifies the empty, labelled Survey-only MySQL schema. */
public class MatchingMysqlChecks extends SurveyMysqlConcurrency {
    static RoommateMatchingService matching;
    static ControlledRefresh refresh;
    static RoommateMatchRepository matches;

    static void runAll() throws Exception {
        matching=context.getBean(RoommateMatchingService.class); refresh=context.getBean(ControlledRefresh.class);
        matches=context.getBean(RoommateMatchRepository.class);
        test("matching-concurrent-create-unique-pair",MatchingMysqlChecks::concurrentMatch);
        test("matching-survey-update-race",MatchingMysqlChecks::surveyRace);
        for(boolean liked:List.of(false,true)) for(UserStatus state:List.of(UserStatus.SUSPENDED,UserStatus.WITHDRAWN)) {
            test("matching-"+(liked?"liked":"all")+"-"+state+"-between-id-and-dto",()->listRace(liked,state));
        }
        test("matching-top-nine-filter-and-query-evidence",MatchingMysqlChecks::recommendations);
        test("matching-physical-constraints-and-owned-rollback",MatchingMysqlChecks::constraints);
        test("matching-ai-shared-user-quota",MatchingMysqlChecks::aiQuota);
    }
    static Long[] pair() {
        Long a=user(), b=user(); save(a,true,false); save(b,false,false); return new Long[]{a,b};
    }
    static void concurrentMatch() throws Exception {
        var ids=pair(); var start=new CountDownLatch(1); List<Future<?>> jobs=new ArrayList<>();
        for(int i=0;i<2;i++) jobs.add(workers.submit(()->{await(start);check(refresh.refreshAnalysis(ids[0],ids[1]).isPresent(),"Missing score");}));
        start.countDown(); for(var job:jobs) job.get(15,TimeUnit.SECONDS);
        check(jdbc.queryForObject("select count(*) from ROOMMATE_MATCH where requester_user_id=? and target_user_id=?",Integer.class,ids[0],ids[1])==1,"Duplicate match pair");
    }
    static void surveyRace() throws Exception {
        var ids=pair(); int before=refresh.refreshAnalysis(ids[0],ids[1]).orElseThrow().score();
        var locked=new CountDownLatch(1); var release=new CountDownLatch(1); var attempting=new CountDownLatch(1);
        var update=workers.submit(()->tx().executeWithoutResult(s->{session();save(ids[1],false,true);context.getBean(jakarta.persistence.EntityManager.class).flush();committed("survey-committed");locked.countDown();await(release);}));
        await(locked);
        var score=workers.submit(()->{attempting.countDown();return refresh.refreshAnalysis(ids[0],ids[1]).orElseThrow();});
        await(attempting); try {expectWaiting(score);} finally {release.countDown();}
        update.get(15,TimeUnit.SECONDS); var result=score.get(15,TimeUnit.SECONDS);
        check(result.score()!=before,"Survey change must affect score");
        check(matching.getRoommateDetail(ids[0],ids[1]).matchScore()==result.score(),"Stale score after update");
        check(profileService.getMyProfileSurvey(ids[1]).answers().meal().equals("각자 먹고 싶음"),"Survey not committed");
    }
    static void listRace(boolean liked,UserStatus state) throws Exception {
        var ids=pair();
        tx().executeWithoutResult(s->context.getBean(ProfileLikeRepository.class).saveAndFlush(
                new ProfileLike(users.getReferenceById(ids[0]),users.getReferenceById(ids[1]))));
        var selected=new CountDownLatch(1);var proceed=new CountDownLatch(1);
        refresh.beforeTargets=who->{if(who.equals(ids[0])){selected.countDown();await(proceed);}};
        var query=workers.submit(()->liked?matching.getLikedRoommates(ids[0],PageRequest.of(0,100))
                :matching.getRoommates(ids[0],null,PageRequest.of(0,100)));
        try {
            await(selected);tx().executeWithoutResult(s->{session();status(ids[1],state);committed("status-committed");});
        } finally {proceed.countDown();}
        try {
            var page=query.get(15,TimeUnit.SECONDS);
            check(page.content().stream().noneMatch(p->p.id().equals(ids[1])),"Inactive user leaked");
            check(page.content().stream().allMatch(Objects::nonNull),"Null DTO");
            if(liked){check(page.totalElements()==0&&page.totalPages()==0&&page.first()&&page.last(),"Liked count mismatch");}
            else {check(page.totalElements()==page.content().size()&&page.first()&&page.last(),"List count mismatch");}
        } finally {refresh.beforeTargets=who->{};}
    }
    static void recommendations() {
        Long requester=user();save(requester,true,false);
        List<Long> candidates=new ArrayList<>();for(int i=0;i<120;i++){Long id=user();save(id,false,false);candidates.add(id);}
        // The query may include earlier fixtures; first determine the actual score ordering.
        var original=matching.getRecommendedRoommates(requester,PageRequest.of(0,9));
        check(original.content().size()==9,"Need nine recommendations");
        Long inside=original.content().getFirst().id();Long outside=candidates.getLast();
        for(Long id:List.of(inside,outside)) profileService.saveOrUpdateMyProfileSurvey(id,
                change(profile(false),"mealPlan",List.of("중식"),ProfileSurveyRequest.class));
        var filter=JSON.convertValue(Map.of("mealPlan",List.of("중식")),RoommateFilterRequest.class);
        long start=System.nanoTime();var result=matching.getRecommendedRoommates(requester,filter,PageRequest.of(0,9));
        long millis=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start);
        check(result.content().stream().map(RoommateProfileResponse::id).toList().equals(List.of(inside)),"Filter must not backfill outside top nine");
        check(result.totalElements()==1&&result.totalPages()==1,"Filtered metadata mismatch");
        var indexes=jdbc.queryForList("select table_name,index_name,non_unique,seq_in_index,column_name from information_schema.statistics where table_schema=database() and lower(table_name) in ('roommate_match','profile_like','profile_survey','user_profile','profile_survey_option') order by table_name,index_name,seq_in_index");
        var ids=original.content().stream().map(p->p.id().toString()).collect(java.util.stream.Collectors.joining(","));
        var plan=jdbc.queryForList("EXPLAIN FORMAT=JSON SELECT p.user_id FROM USER_PROFILE p JOIN `USER` u ON u.user_id=p.user_id WHERE p.user_id IN ("+ids+") AND u.status='ACTIVE' AND EXISTS (SELECT 1 FROM PROFILE_SURVEY s JOIN PROFILE_SURVEY_OPTION o ON o.profile_survey_id=s.profile_survey_id WHERE s.user_id=p.user_id AND s.completed=true AND o.field_code='MEAL_PLAN' AND o.option_value='중식')");
        results.add(Map.of("case","matching-query-measurement","result","PASS","newCandidates",120,
                "filteredRequestMillis",millis,"indexes",indexes,"representativeFilterExplain",plan));
    }
    static void constraints() {
        var ids=pair();refresh.refreshAnalysis(ids[0],ids[1]);
        try {
            tx().executeWithoutResult(s->matches.saveAndFlush(new RoommateMatch(users.getReferenceById(ids[0]),users.getReferenceById(ids[1]),70,null)));
            throw new AssertionError("Expected UNIQUE rejection");
        } catch(org.springframework.dao.DataIntegrityViolationException expected) { }
        Long third=user();
        Long existingPk=jdbc.queryForObject("select match_id from ROOMMATE_MATCH where requester_user_id=? and target_user_id=?",Long.class,ids[0],ids[1]);
        try {
            tx().executeWithoutResult(s->jdbc.update("insert into ROOMMATE_MATCH(match_id,requester_user_id,target_user_id,match_score) values(?,?,?,70)",existingPk,third,ids[1]));
            throw new AssertionError("Expected PK rejection");
        } catch(org.springframework.dao.DataIntegrityViolationException expected) { }
        try {
            tx().executeWithoutResult(s->jdbc.update("insert into ROOMMATE_MATCH(requester_user_id,target_user_id,match_score) values(?,?,70)",ids[0],Long.MAX_VALUE));
            throw new AssertionError("Expected FK rejection");
        } catch(org.springframework.dao.DataIntegrityViolationException expected) { }
        int before=jdbc.queryForObject("select match_score from ROOMMATE_MATCH where requester_user_id=? and target_user_id=?",Integer.class,ids[0],ids[1]);
        tx().executeWithoutResult(s->{jdbc.update("update ROOMMATE_MATCH set match_score=1 where requester_user_id=? and target_user_id=?",ids[0],ids[1]);s.setRollbackOnly();});
        check(before==jdbc.queryForObject("select match_score from ROOMMATE_MATCH where requester_user_id=? and target_user_id=?",Integer.class,ids[0],ids[1]),"Rollback changed score");
    }
    static void aiQuota() throws Exception {
        Long id=user();
        String key=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(("ai-analysis:user:"+id).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        // This JPA-only harness preserves @Table uppercase identifiers; Boot uses lowercase names.
        // Adapt physical identifiers only. Keep the real store's collation checks and quota SQL intact.
        var quotaJdbc=new org.springframework.jdbc.core.JdbcTemplate(pool) {
            String physical(String sql) {
                return sql.replace("auth_limit_bucket","AUTH_LIMIT_BUCKET")
                        .replace("table_name = ?","LOWER(table_name) = LOWER(?)")
                        .replace("table_name = 'auth_email_identity'","LOWER(table_name) = 'auth_email_identity'");
            }
            @Override public List<Map<String,Object>> queryForList(String sql,Object... args) {
                return super.queryForList(physical(sql),args);
            }
            @Override public int update(String sql,Object... args) {
                return super.update(physical(sql),args);
            }
            @Override public <T> List<T> query(String sql,org.springframework.jdbc.core.RowMapper<T> mapper,Object... args) {
                return super.query(physical(sql),mapper,args);
            }
        };
        var first=new AiAnalysisRateLimiter(new com.kkirikkiri.auth.repository.AuthLimitStore(quotaJdbc,manager),2,60);
        var second=new AiAnalysisRateLimiter(new com.kkirikkiri.auth.repository.AuthLimitStore(quotaJdbc,manager),2,60);
        var start=new CountDownLatch(1);List<Future<Integer>> jobs=new ArrayList<>();
        try {
            for(var limiter:List.of(first,second)) jobs.add(workers.submit(()->{
                await(start);int accepted=0;
                for(int i=0;i<3;i++) try {limiter.reserve(id);accepted++;}
                catch(com.kkirikkiri.global.exception.CustomException failure) {
                    check(failure.getErrorCode()==com.kkirikkiri.global.exception.ErrorCode.AI_ANALYSIS_RATE_LIMITED,"Wrong quota error");
                }
                return accepted;
            }));
            start.countDown();int accepted=0;for(var job:jobs)accepted+=job.get(15,TimeUnit.SECONDS);
            check(accepted==2,"Shared MySQL quota exceeded");
        } finally {quotaJdbc.update("delete from auth_limit_bucket where bucket_key=?",key);}
    }
    @Configuration
    @Import({SurveyMysqlConcurrency.Config.class,RoommateMatchingService.class,MatchingInputRepository.class,
            RoommateMatchCalculator.class,ControlledRefresh.class})
    static class Config { }

    public static class ControlledRefresh extends MatchingRefreshService {
        volatile Consumer<Long> beforeTargets=who->{};
        public ControlledRefresh(PlatformTransactionManager manager,MatchingInputRepository inputs,UserRepository users,
                UserProfileRepository profiles,ProfileSurveyRepository surveys,RoommatePreferenceRepository preferences,
                RoommatePreferenceOptionRepository options,PreferenceCriterionRepository criteria,
                RoommateMatchRepository matches,RoommateMatchCalculator calculator) {
            super(manager,inputs,users,profiles,surveys,preferences,options,criteria,matches,calculator);
        }
        @Override public void refreshTargets(Long requester,List<Long> targets) {
            beforeTargets.accept(requester);super.refreshTargets(requester,targets);
        }
    }
}
