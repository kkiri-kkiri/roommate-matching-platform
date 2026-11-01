package exhibition;

import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.kkirikkiri.global.init.*;
import com.kkirikkiri.user.entity.*;
import com.kkirikkiri.user.repository.*;
import com.kkirikkiri.community.post.entity.CommunityPost;
import com.kkirikkiri.community.post.repository.CommunityPostRepository;
import com.kkirikkiri.community.comment.service.CommunityCommentService;
import com.kkirikkiri.community.comment.dto.CommunityCommentCreateRequest;
import com.kkirikkiri.chat.service.ChatService;
import com.kkirikkiri.chat.dto.ChatRoomCreateRequest;
import com.kkirikkiri.chat.entity.ChatRoomCategory;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes=ExhibitionConfiguration.class,webEnvironment=SpringBootTest.WebEnvironment.NONE)
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class DemoDataMergeIntegrationTest {
    private static final String PASSWORD="Fictional123!";
    private static final LocalDate ANCHOR=LocalDate.of(2026,10,31);
    @TempDir Path directory;
    @Autowired ConfigurableApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    private DemoDataMerge.Source source;
    private DemoDataMerge merger;
    private DemoDataMerge.Receipt receipt;
    private Path receiptFile;
    private JdbcTemplate sourceJdbc;
    private long manualUser;
    private <T> T bean(Class<T> type){return context.getBean(type);}
    @BeforeEach void prepare() throws Exception {
        try(var connection=Objects.requireNonNull(jdbc.getDataSource()).getConnection()){
            assertTrue(connection.getMetaData().getURL().startsWith("jdbc:h2:mem:"));
        }
        ExhibitionTool.assertIsolated(context);
        new ResourceDatabasePopulator(new ClassPathResource("data-common.sql")).execute(jdbc.getDataSource());
        var legacy=new SeedUserManifest(directory.resolve("source-users.properties").toString());
        var planner=new ExhibitionPlanner(new ExhibitionDatabase(jdbc),bean(UserRepository.class),legacy,manager);
        ExhibitionJournal.State generated;
        try(var journal=new ExhibitionJournal(directory.resolve("source.json"))){
            var empty=new ExhibitionJournal.State(DemoDataExpansion.identity(jdbc),ANCHOR.toString(),"COMPLETE",Map.of());
            var plan=planner.preview(empty,ANCHOR,true);
            generated=bean(ExhibitionWriter.class).apply(journal,empty,planner,plan,plan.token(),legacy,PASSWORD);
        }
        // The actual latest source migrated historical nonanonymous posts without changing their anonymity.
        jdbc.update("UPDATE community_post SET anonymous=FALSE");
        Path script=directory.resolve("source.sql");
        jdbc.execute("SCRIPT TO '"+script.toString().replace('\\','/').replace("'","''")+"'");
        sourceJdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:merge-source-"+UUID.randomUUID()+";MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1","sa",""));
        sourceJdbc.execute("RUNSCRIPT FROM '"+script.toString().replace('\\','/').replace("'","''")+"'");
        var sourceSnapshot=DemoDataSnapshot.capture(sourceJdbc); Map<String,Long> scenarios=new TreeMap<>();
        generated.owned().forEach((key,value)->{if(value.kind().equals("user"))scenarios.put(key,value.id());});
        var sourceReceipt=new DemoDataExpansion.Receipt(DemoDataExpansion.identity(sourceJdbc),generated.database(),ANCHOR.toString(),"COMPLETE",DemoDataExpansion.catalog(),sourceSnapshot,sourceSnapshot,scenarios);
        source=new DemoDataMerge.Source(sourceJdbc,sourceReceipt,generated);
        var tables=DemoDataSnapshot.capture(jdbc).tables().keySet();
        jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
        for(String table:tables)jdbc.execute("TRUNCATE TABLE `"+table+"` RESTART IDENTITY");
        jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");
        bean(jakarta.persistence.EntityManager.class).clear();
        new ResourceDatabasePopulator(new ClassPathResource("data-common.sql")).execute(jdbc.getDataSource());
        new TransactionTemplate(manager).executeWithoutResult(status->{
            long first=createUser("existing@hknu.ac.kr","기존일반",UserRole.USER);
            createUser("existing-admin@hknu.ac.kr","기존관리자",UserRole.ADMIN);
            for(int i=0;i<3;i++)bean(CommunityPostRepository.class).saveAndFlush(new CommunityPost(bean(UserRepository.class).findById(first).orElseThrow(),
                    bean(DormitoryRepository.class).findByDormitoryName("나래").orElseThrow(),"자유게시판","수동 글 "+i,"출처 불명 내용 유지 "+i));
            var rows=new DemoMergeRows(jdbc);
            String[] mbtis={"ISTJ","ISFJ","INFJ","INTJ","ISTP","ISFP","INFP","INTP","ESTP","ESFP","ENFP","ENTP","ESTJ","ESFJ","ENFJ","ENTJ"};
            for(int slot=1;slot<=30;slot++){
                long uid=createUser("seed_user%02d@hknu.ac.kr".formatted(slot),"테스트룸메%02d".formatted(slot),UserRole.USER);
                jdbc.update("UPDATE user_profile SET birth_year=?,admission_year=?,grade=?,mbti=? WHERE user_id=?",2000+(slot-1)%5,"%02d".formatted(22+(slot-1)%4),(1+(slot-1)%4)+"학년",mbtis[(slot-1)%16],uid);
                long survey=rows.insert("profile_survey",SeedDataService.legacySurveyValues(uid,slot));
                var days=slot%3==0?List.of("월","화","수","목","금","토","일"):slot%3==1?List.of("월","화","수","목","금"):List.of("월","화","수","목","금","토");
                var meals=switch(slot%4){case 0->List.of("신청 안 함");case 1->List.of("조식","석식");case 2->List.of("중식","석식");default->List.of("조식","중식","석식");};
                for(String day:days)rows.insert("profile_survey_option",Map.of("profile_survey_id",survey,"field_code","DORM_STAY_DAY","option_value",day));
                for(String meal:meals)rows.insert("profile_survey_option",Map.of("profile_survey_id",survey,"field_code","MEAL_PLAN","option_value",meal));
                new CommunitySeedDataService(bean(CommunityPostRepository.class),jdbc).createPosts(bean(UserRepository.class).findById(uid).orElseThrow(),bean(DormitoryRepository.class).findByDormitoryName("나래").orElseThrow(),slot);
            }
            manualUser=createUser("manual@hknu.ac.kr","수동회원",UserRole.USER);
        });
        bean(jakarta.persistence.EntityManager.class).clear();
        assertEquals(33L,jdbc.queryForObject("SELECT COUNT(*) FROM `user`",Long.class));
        long post=jdbc.queryForObject("SELECT MAX(post_id) FROM community_post",Long.class);
        bean(CommunityCommentService.class).createComment(manualUser,post,new CommunityCommentCreateRequest("기존 수동 댓글 그대로",false));
        bean(ChatService.class).createNewRoom(manualUser,new ChatRoomCreateRequest(null,ChatRoomCategory.COMMUNITY,post,null,"POST"));
        var baseline=DemoDataSnapshot.capture(jdbc);
        receipt=new DemoDataMerge.Receipt(DemoDataMerge.VERSION,DemoDataExpansion.identity(jdbc),"original-base",sourceReceipt.database(),"READY",baseline,baseline,sourceSnapshot,null,Map.of(),null);
        merger=new DemoDataMerge(context,source);receiptFile=directory.resolve("merge.json");
        DemoDataMerge.write(receiptFile,receipt);
    }
    private long createUser(String email,String nickname,UserRole role){
        var user=new User(bean(PasswordEncoder.class).encode(PASSWORD),email,role);user.verifyEmail();bean(UserRepository.class).saveAndFlush(user);
        var faculty=bean(FacultyRepository.class).findByFacultyName("컴퓨터응용수학부").orElseThrow();
        bean(UserProfileRepository.class).saveAndFlush(new UserProfile(user,nickname,"남자",bean(DormitoryRepository.class).findByDormitoryName("나래").orElseThrow(),faculty,
                bean(MajorRepository.class).findByFaculty_FacultyIdAndMajorName(faculty.getFacultyId(),"소프트웨어융합전공").orElseThrow(),(short)2001,"23","3학년","INTJ"));
        return user.getUserId();
    }
    @Test void mergePreservesAccountsManualRowsAndSourceWhileRemappingAllRelationshipsAndRepeatingWithoutWrites() throws Exception {
        var plan=merger.preview(receipt);assertEquals(50,plan.slots().size());assertEquals(80,plan.posts().size());assertTrue(plan.skipped().isEmpty());
        assertEquals(receipt.baseline(),DemoDataSnapshot.capture(jdbc));
        assertThrows(IllegalStateException.class,()->merger.apply(receiptFile,receipt,plan,"0".repeat(64),PASSWORD));
        var merged=merger.apply(receiptFile,receipt,plan,plan.token(),PASSWORD);
        assertEquals(receipt.baseline().tables().get("user").entrySet(),merged.completed().tables().get("user").entrySet().stream()
                .filter(e->receipt.baseline().tables().get("user").containsKey(e.getKey())).collect(java.util.stream.Collectors.toSet()));
        assertEquals(54L,jdbc.queryForObject("SELECT COUNT(*) FROM `user`",Long.class));
        assertEquals(83L,jdbc.queryForObject("SELECT COUNT(*) FROM community_post",Long.class));
        assertEquals(161L,jdbc.queryForObject("SELECT COUNT(*) FROM community_comment",Long.class));
        assertEquals(9L,jdbc.queryForObject("SELECT COUNT(*) FROM chat_room",Long.class));
        assertEquals(2450L,jdbc.queryForObject("SELECT COUNT(*) FROM roommate_match",Long.class));
        long adminSource=source.receipt().scenarios().get(ExhibitionCatalog.MODERATOR_KEY);
        assertNotEquals(manualUser,merged.ids().get("user").get(adminSource));
        assertNotEquals(source.journal().owned().get("post/자유게시판/1").id(),merged.ids().get("community_post").get(source.journal().owned().get("post/자유게시판/1").id()));
        var after=DemoDataSnapshot.capture(jdbc);var repeated=merger.preview(merged);
        merger.apply(receiptFile,merged,repeated,repeated.token(),null);
        assertEquals(after,DemoDataSnapshot.capture(jdbc));assertEquals(receipt.source(),DemoDataSnapshot.capture(sourceJdbc));
    }
    @Test void unknownLegacyEditIsSkippedTogetherWithDependentContent() throws Exception {
        jdbc.update("UPDATE user_profile SET introduction='수동 편집 보존' WHERE user_id=7");
        var baseline=DemoDataSnapshot.capture(jdbc);
        var changed=new DemoDataMerge.Receipt(receipt.version(),receipt.database(),receipt.base(),receipt.latest(),receipt.status(),baseline,baseline,receipt.source(),null,Map.of(),null);
        var plan=merger.preview(changed);assertFalse(plan.slots().contains(5));assertTrue(plan.skipped().containsKey("user/5"));
        var result=merger.apply(receiptFile,changed,plan,plan.token(),PASSWORD);
        assertEquals("수동 편집 보존",jdbc.queryForObject("SELECT introduction FROM user_profile WHERE user_id=7",String.class));
        assertTrue(result.plan().skipped().containsKey("post/자유게시판/5"));
        assertEquals(receipt.source(),DemoDataSnapshot.capture(sourceJdbc));
    }
    @Test void lateConstraintFailureRollsBackEveryCoreWriteAndLeavesAnIncompleteBarrier() {
        String content=sourceJdbc.queryForObject("SELECT content FROM chat_message ORDER BY message_id LIMIT 1",String.class);
        jdbc.execute("ALTER TABLE chat_message ADD CONSTRAINT merge_failure CHECK (content<>'"+content.replace("'","''")+"')");
        var before=DemoDataSnapshot.capture(jdbc);var plan=merger.preview(receipt);
        assertThrows(RuntimeException.class,()->merger.apply(receiptFile,receipt,plan,plan.token(),PASSWORD));
        assertEquals(before,DemoDataSnapshot.capture(jdbc));
        assertEquals("APPLYING",assertDoesNotThrow(()->DemoDataMerge.read(receiptFile)).status());
        assertThrows(IllegalStateException.class,()->merger.preview(assertDoesNotThrow(()->DemoDataMerge.read(receiptFile))));
    }
}
