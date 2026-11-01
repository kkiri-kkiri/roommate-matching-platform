package exhibition;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.kkirikkiri.global.init.*;
import com.kkirikkiri.user.repository.*;
import com.kkirikkiri.community.comment.service.CommunityAnonymousAuthorService;
import com.kkirikkiri.community.post.repository.CommunityPostRepository;
import com.kkirikkiri.community.comment.repository.CommunityCommentRepository;
import com.kkirikkiri.chat.entity.*;
import com.kkirikkiri.matching.service.MatchingRefreshService;
import org.springframework.security.crypto.password.PasswordEncoder;
import jakarta.validation.Validator;
import com.kkirikkiri.auth.dto.SignupRequest;
import tools.jackson.databind.json.JsonMapper;
import static exhibition.DemoMergeRows.*;

/** Reviewed, provenance-limited merge. Existing account rows are never written. */
public final class DemoDataMerge {
    public static final String VERSION = "base-merge-v1-preserve-accounts";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Set<String> LEGACY_COLUMNS = Set.of("bug_handling", "hair_dryer_use");
    static final Map<String, Map<String, String>> FK = Map.ofEntries(
        Map.entry("user_profile", Map.of("user_id","user","dormitory_id","dormitory","faculty_id","faculty","major_id","major")),
        Map.entry("profile_survey", Map.of("user_id","user")),
        Map.entry("profile_survey_option", Map.of("profile_survey_id","profile_survey")),
        Map.entry("roommate_preference", Map.of("user_id","user")),
        Map.entry("roommate_preference_option", Map.of("preference_id","roommate_preference")),
        Map.entry("preference_criterion", Map.of("preference_id","roommate_preference")),
        Map.entry("community_post", Map.of("author_id","user","dormitory_id","dormitory")),
        Map.entry("community_comment", Map.of("post_id","community_post","author_id","user","parent_comment_id","community_comment")),
        Map.entry("post_like", Map.of("post_id","community_post","user_id","user")),
        Map.entry("comment_like", Map.of("comment_id","community_comment","user_id","user")),
        Map.entry("community_anonymous_author", Map.of("post_id","community_post","author_id","user")),
        Map.entry("calendar_category", Map.of("owner_user_id","user")),
        Map.entry("calendar_category_setting", Map.of("category_id","calendar_category","user_id","user")),
        Map.entry("calendar_event", Map.of("category_id","calendar_category","owner_user_id","user")),
        Map.entry("chat_room", Map.of("source_post_id","community_post","source_comment_id","community_comment")),
        Map.entry("chat_room_member", Map.of("chat_room_id","chat_room","user_id","user")),
        Map.entry("chat_message", Map.of("chat_room_id","chat_room","sender_id","user")),
        Map.entry("report", Map.of("post_id","community_post","comment_id","community_comment","processed_by","user","reporter_id","user","target_user_id","user")));
    public record Plan(Set<Integer> slots, Map<Long,Long> users, Map<Long,Long> posts, Map<String,String> skipped,
            Map<String,Set<String>> mutable, Map<String,Map<Long,String>> fixed, String token) {}
    public record Receipt(String version, String database, String base, String latest, String status,
            DemoDataSnapshot originalBase, DemoDataSnapshot baseline, DemoDataSnapshot source,
            DemoDataSnapshot completed, Map<String,Map<Long,Long>> ids, Plan plan) {}
    public record Source(JdbcTemplate jdbc, DemoDataExpansion.Receipt receipt, ExhibitionJournal.State journal) {}
    private final ConfigurableApplicationContext context;
    private final JdbcTemplate jdbc;
    private final DemoMergeRows target;
    private final Source source;
    private final DemoMergeRows latest;
    private final Map<String,List<Map<String,Object>>> content = new TreeMap<>();
    public DemoDataMerge(ConfigurableApplicationContext context, Source source) {
        this.context=context; this.jdbc=context.getBean(JdbcTemplate.class); this.target=new DemoMergeRows(jdbc);
        this.source=source; this.latest=new DemoMergeRows(source.jdbc());
        PK.keySet().forEach(t -> content.put(t, latest.rows(t)));
    }
    public static Receipt read(Path path) throws IOException { return JSON.readValue(java.nio.file.Files.readString(path),Receipt.class); }
    public static void write(Path path, Receipt receipt) throws IOException {
        // Same atomic replace mechanics as the expansion receipt; no source files are ever rewritten.
        java.nio.file.Files.createDirectories(path.toAbsolutePath().getParent());
        Path temp=java.nio.file.Files.createTempFile(path.toAbsolutePath().getParent(),"merge-", ".tmp");
        try { java.nio.file.Files.writeString(temp,JSON.writeValueAsString(receipt));
            java.nio.file.Files.move(temp,path,java.nio.file.StandardCopyOption.ATOMIC_MOVE,java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } finally { java.nio.file.Files.deleteIfExists(temp); }
    }
    public static DemoDataSnapshot normalizedBase(JdbcTemplate jdbc) {
        var snapshot=DemoDataSnapshot.capture(jdbc); var tables=new TreeMap<>(snapshot.tables());
        tables.remove("profile_survey_legacy_20261101");
        Map<String,String> surveys=new TreeMap<>();
        for(var row:new ExhibitionDatabase(jdbc).rows("SELECT * FROM profile_survey")) {
            var projected=new TreeMap<>(row); LEGACY_COLUMNS.forEach(projected::remove);
            surveys.put(rowKey("profile_survey",row),ExhibitionJournal.hash(projected));
        }
        tables.put("profile_survey",surveys); return new DemoDataSnapshot(tables);
    }
    public static void verifyLegacyArchive(JdbcTemplate base, JdbcTemplate target) {
        var old=new ExhibitionDatabase(base).rows("SELECT profile_survey_id,user_id,bug_handling,hair_dryer_use FROM profile_survey ORDER BY profile_survey_id");
        var archived=new ExhibitionDatabase(target).rows("SELECT profile_survey_id,user_id,bug_handling,hair_dryer_use FROM profile_survey_legacy_20261101 ORDER BY profile_survey_id");
        if(!old.equals(archived)) throw new IllegalStateException("Legacy archive differs from original base");
        if(!normalizedBase(base).equals(normalizedBase(target))) throw new IllegalStateException("Target differs from base outside archived columns");
    }
    public void validateSource() {
        if(!"COMPLETE".equals(source.receipt().status()) || !DemoDataExpansion.catalog().equals(source.receipt().catalog())
                || !DemoDataExpansion.identity(source.jdbc()).equals(source.receipt().database())
                || !DemoDataSnapshot.capture(source.jdbc()).equals(source.receipt().completed()))
            throw new IllegalStateException("Latest fifty-user source is not the verified immutable source");
        if(!"COMPLETE".equals(source.journal().status()) || !(source.journal().database().equals(source.receipt().database())
                || source.journal().database().equals(source.receipt().source()))) throw new IllegalStateException("Source journal identity mismatch");
        for(var s:ExhibitionCatalog.students()) {
            var row=latest.one("user",source.receipt().scenarios().get(s.key()));
            if(!s.email().equals(row.get("school_email")) || !"USER".equals(row.get("role"))) throw new IllegalStateException("Source student identity mismatch");
            var profile=content.get("user_profile").stream().filter(p -> same(p.get("user_id"),row.get("user_id"))).findFirst().orElseThrow();
            if(!s.nickname().equals(profile.get("nickname"))) throw new IllegalStateException("Source nickname mismatch");
        }
        for(var p:ExhibitionCatalog.posts()) {
            var marker=Objects.requireNonNull(source.journal().owned().get(p.key()));
            var row=latest.one("community_post",marker.id());
            if(!p.title().equals(row.get("title")) || !p.content().equals(row.get("content"))
                    || !same(row.get("author_id"),source.receipt().scenarios().get("user/"+p.slot()))) throw new IllegalStateException("Source post provenance mismatch");
            for(String child:List.of("comment","reply")) {
                var c=latest.one("community_comment",marker.children().get(child));
                long author=source.receipt().scenarios().get("user/"+(child.equals("comment")?ExhibitionWriter.commenterSlot(p.slot()):p.slot()));
                if(!same(c.get("post_id"),marker.id()) || !same(c.get("author_id"),author)
                        || !(child.equals("comment")?p.comment():p.reply()).equals(c.get("content"))
                        || !(child.equals("comment")?c.get("parent_comment_id")==null:same(c.get("parent_comment_id"),marker.children().get("comment")))) throw new IllegalStateException("Source comment provenance mismatch");
            }
        }
    }
    public Plan preview(Receipt receipt) {
        requireReceipt(receipt);
        var current=DemoDataSnapshot.capture(jdbc);
        if(!current.equals(receipt.status().equals("COMPLETE")?receipt.completed():receipt.baseline())) throw new IllegalStateException("Target changed since checkpoint");
        validateSource();
        if(receipt.status().equals("COMPLETE")) return receipt.plan();
        Map<String,String> skipped=new TreeMap<>(); Set<Integer> slots=new TreeSet<>();
        Map<Long,Long> users=new TreeMap<>(), posts=new TreeMap<>();
        Map<String,Set<String>> mutable=new TreeMap<>(); Map<String,Map<Long,String>> fixed=new TreeMap<>();
        // Explicit email identity mapping retains the entire existing account row. No manifest is invented.
        for(var row:content.get("user")) {
            var candidates=target.query("SELECT * FROM `user` WHERE school_email=?",row.get("school_email"));
            if(candidates.size()==1 && same(candidates.getFirst().get("role"),row.get("role")))
                users.put(id("user",row),id("user",candidates.getFirst()));
        }
        for(var student:ExhibitionCatalog.students()) {
            long sid=source.receipt().scenarios().get(student.key());
            if(student.slot()>30) {
                if(users.containsKey(sid) || !target.query("SELECT user_id FROM `user` WHERE school_email=?",student.email()).isEmpty()
                        || !target.query("SELECT user_id FROM user_profile WHERE nickname=?",student.nickname()).isEmpty())
                    { skipped.put(student.key(),"new identity/nickname already exists; no adoption"); users.remove(sid); }
                else slots.add(student.slot());
                continue;
            }
            Long uid=users.get(sid);
            if(uid==null || !legacyStudent(uid,student.slot())) {
                skipped.put(student.key(),"legacy profile/survey/options or account ownership differs; preserved");
                users.remove(sid); continue;
            }
            if(!target.query("SELECT user_id FROM user_profile WHERE nickname=? AND user_id<>?",student.nickname(),uid).isEmpty()) {
                skipped.put(student.key(),"latest nickname belongs to another account"); users.remove(sid); continue;
            }
            slots.add(student.slot());
            var profile=target.query("SELECT * FROM user_profile WHERE user_id=?",uid).getFirst();
            authorize("user_profile",profile,Set.of("user_profile_id","user_id","created_at"),mutable,fixed);
            var survey=target.query("SELECT * FROM profile_survey WHERE user_id=?",uid).getFirst();
            authorize("profile_survey",survey,Set.of("profile_survey_id","user_id","created_at"),mutable,fixed);
            for(var option:target.query("SELECT * FROM profile_survey_option WHERE profile_survey_id=?",id("profile_survey",survey)))
                mutable.computeIfAbsent("profile_survey_option",k -> new TreeSet<>()).add(rowKey("profile_survey_option",option));
        }
        Set<Long> oldCohort=new HashSet<>();
        for(var s:ExhibitionCatalog.students().subList(0,30)) if(slots.contains(s.slot())) oldCohort.add(users.get(source.receipt().scenarios().get(s.key())));
        if(target.rows("roommate_match").stream().anyMatch(r -> oldCohort.contains(number(r.get("requester_user_id"))) && oldCohort.contains(number(r.get("target_user_id")))))
            throw new IllegalStateException("Existing cohort cache needs a separate reviewed update plan");
        long admin=source.receipt().scenarios().get(ExhibitionCatalog.MODERATOR_KEY);
        var sourceAdmin=latest.one("user",admin);
        var adminProfile=content.get("user_profile").stream().filter(p -> same(p.get("user_id"),admin)).findFirst().orElseThrow();
        if(!target.query("SELECT user_id FROM `user` WHERE school_email=?",sourceAdmin.get("school_email")).isEmpty()
                || !target.query("SELECT user_id FROM user_profile WHERE nickname=?",adminProfile.get("nickname")).isEmpty()) {
            users.remove(admin); skipped.put(ExhibitionCatalog.MODERATOR_KEY,"existing unowned moderator identity; do not replace");
        }
        for(var post:ExhibitionCatalog.posts()) {
            var owned=source.journal().owned().get(post.key()); Long owner=users.get(source.receipt().scenarios().get("user/"+post.slot()));
            var legacy=CommunitySeedDataService.legacyPosts().stream().filter(p -> p.index()==post.slot() && p.category().equals(post.category())).findFirst().orElseThrow();
            var candidates=owner==null?List.<Map<String,Object>>of():target.query("SELECT * FROM community_post WHERE author_id=? AND category=? AND title=? AND content=?",owner,legacy.category(),legacy.title(),legacy.content());
            var sourcePost=latest.one("community_post",owned.id());
            if(candidates.size()!=1 || !"ACTIVE".equals(candidates.getFirst().get("status"))
                    || !same(candidates.getFirst().get("anonymous"),sourcePost.get("anonymous"))) {
                skipped.put(post.key(),"full legacy signature/owner/status/anonymity differs; preserve post and dependent data"); continue;
            }
            var row=candidates.getFirst(); posts.put(owned.id(),id("community_post",row));
            var columns=new TreeSet<>(row.keySet()); columns.removeAll(Set.of("title","content"));
            authorize("community_post",row,columns,mutable,fixed);
        }
        String token=ExhibitionJournal.hash(List.of(VERSION,receipt.database(),receipt.baseline(),receipt.source(),slots,users,posts,skipped,mutable,fixed));
        return new Plan(slots,users,posts,skipped,mutable,fixed,token);
    }
    private boolean legacyStudent(long uid,int slot) {
        var user=target.one("user",uid);
        if(!"USER".equals(user.get("role")) || !"ACTIVE".equals(user.get("status")) || !truth(user.get("email_verified"))) return false;
        var profiles=target.query("SELECT p.*,d.dormitory_name,f.faculty_name,m.major_name FROM user_profile p JOIN dormitory d ON d.dormitory_id=p.dormitory_id JOIN faculty f ON f.faculty_id=p.faculty_id LEFT JOIN major m ON m.major_id=p.major_id WHERE p.user_id=?",uid);
        if(profiles.size()!=1) return false; var p=profiles.getFirst();
        String[] mbtis={"ISTJ","ISFJ","INFJ","INTJ","ISTP","ISFP","INFP","INTP","ESTP","ESFP","ENFP","ENTP","ESTJ","ESFJ","ENFJ","ENTJ"};
        Map<String,Object> expected=Map.of("nickname","테스트룸메%02d".formatted(slot),"gender","남자","birth_year",2000+(slot-1)%5,
                "admission_year","%02d".formatted(22+(slot-1)%4),"grade",(1+(slot-1)%4)+"학년","mbti",mbtis[(slot-1)%16],
                "dormitory_name","나래","faculty_name","컴퓨터응용수학부","major_name","소프트웨어융합전공");
        if(expected.entrySet().stream().anyMatch(e -> !same(e.getValue(),p.get(e.getKey()))) || p.get("introduction")!=null || p.get("profile_image_url")!=null) return false;
        var surveys=target.query("SELECT * FROM profile_survey WHERE user_id=?",uid);
        if(surveys.size()!=1 || !target.query("SELECT preference_id FROM roommate_preference WHERE user_id=?",uid).isEmpty()) return false;
        var s=surveys.getFirst(); var values=new TreeMap<>(SeedDataService.legacySurveyValues(uid,slot));
        values.keySet().removeAll(Set.of("created_at","updated_at"));
        if(values.entrySet().stream().anyMatch(e -> !same(e.getValue(),s.get(e.getKey())))) return false;
        Map<String,Set<String>> actual=new TreeMap<>();
        for(var row:target.query("SELECT field_code,option_value FROM profile_survey_option WHERE profile_survey_id=?",id("profile_survey",s)))
            actual.computeIfAbsent(row.get("field_code").toString(),k -> new TreeSet<>()).add(row.get("option_value").toString());
        var days=slot%3==0?List.of("월","화","수","목","금","토","일"):slot%3==1?List.of("월","화","수","목","금"):List.of("월","화","수","목","금","토");
        var meals=switch(slot%4){case 0 -> List.of("신청 안 함");case 1 -> List.of("조식","석식");case 2 -> List.of("중식","석식");default -> List.of("조식","중식","석식");};
        return actual.equals(Map.of("DORM_STAY_DAY",new TreeSet<>(days),"MEAL_PLAN",new TreeSet<>(meals)));
    }
    private static void authorize(String table,Map<String,Object> row,Set<String> columns,Map<String,Set<String>> mutable,Map<String,Map<Long,String>> fixed) {
        mutable.computeIfAbsent(table,k -> new TreeSet<>()).add(rowKey(table,row));
        var projected=new TreeMap<String,String>(); columns.forEach(c -> projected.put(c,row.get(c)==null?null:row.get(c).toString()));
        fixed.computeIfAbsent(table,k -> new TreeMap<>()).put(id(table,row),ExhibitionJournal.hash(projected));
    }
    private void requireReceipt(Receipt receipt) {
        if(!VERSION.equals(receipt.version()) || !Set.of("READY","COMPLETE").contains(receipt.status())
                || !receipt.source().equals(source.receipt().completed())) throw new IllegalStateException("Wrong source/version or incomplete merge; restore a verified checkpoint");
    }

    public Receipt apply(Path path,Receipt receipt,Plan reviewed,String expected,String newPassword) throws IOException {
        Plan plan=preview(receipt);
        if(!plan.token().equals(expected) || !reviewed.equals(plan)) throw new IllegalStateException("Stale merge plan");
        if(receipt.status().equals("COMPLETE")) { verify(receipt); return receipt; }
        if(newPassword==null || !context.getBean(Validator.class).validateValue(SignupRequest.class,"password",newPassword).isEmpty())
            throw new IllegalStateException("A private initial password satisfying signup policy is required for NEW accounts only");
        write(path,new Receipt(VERSION,receipt.database(),receipt.base(),receipt.latest(),"APPLYING",receipt.originalBase(),receipt.baseline(),receipt.source(),null,Map.of(),plan));
        Map<String,Map<Long,Long>> ids=new TreeMap<>(); ids.put("user",new TreeMap<>(plan.users())); ids.put("community_post",new TreeMap<>(plan.posts()));
        Map<String,String> skipped=new TreeMap<>(plan.skipped());
        new TransactionTemplate(context.getBean(PlatformTransactionManager.class)).executeWithoutResult(status -> {
            var repo=context.getBean(UserRepository.class);
            target.rows("user").stream().map(r -> id("user",r)).sorted().forEach(uid -> repo.findByIdForAccountUpdate(uid).orElseThrow());
            for(String table:List.of("community_post","community_comment","chat_room","calendar_category"))
                jdbc.queryForList("SELECT `"+PK.get(table)+"` FROM `"+table+"` ORDER BY `"+PK.get(table)+"` FOR UPDATE");
            if(!DemoDataSnapshot.capture(jdbc).equals(receipt.baseline())) throw new IllegalStateException("Baseline changed while locking");
            importReferences(ids);
            for(var student:ExhibitionCatalog.students()) if(student.slot()>30 && plan.slots().contains(student.slot()))
                insertAccount(source.receipt().scenarios().get(student.key()),ids,newPassword);
            long admin=source.receipt().scenarios().get(ExhibitionCatalog.MODERATOR_KEY);
            if(!skipped.containsKey(ExhibitionCatalog.MODERATOR_KEY)) insertAccount(admin,ids,newPassword);
            importProfilesAndSurveys(plan,ids);
            for(var entry:plan.posts().entrySet()) target.update("community_post",entry.getValue(),mapped("community_post",latest.one("community_post",entry.getKey()),ids));
            importComments(ids,skipped);
            copyRows("post_like",ids,skipped); copyRows("comment_like",ids,skipped);
            importAliases(ids,skipped);
            importCategories(ids,skipped); copyRows("calendar_category_setting",ids,skipped); copyRows("calendar_event",ids,skipped);
            importChats(ids,skipped); copyRows("chat_room_member",ids,skipped); copyRows("chat_message",ids,skipped); copyRows("report",ids,skipped);
            context.getBean(jakarta.persistence.EntityManager.class).flush(); context.getBean(jakarta.persistence.EntityManager.class).clear();
            assertProtected(receipt,plan,DemoDataSnapshot.capture(jdbc));
        });
        var matching=context.getBean(MatchingRefreshService.class);
        List<Long> cohort=ExhibitionCatalog.students().stream().filter(s -> plan.slots().contains(s.slot())).map(s -> ids.get("user").get(source.receipt().scenarios().get(s.key()))).toList();
        for(Long uid:cohort) matching.refreshTargets(uid,cohort.stream().filter(other -> !other.equals(uid)).toList());
        var finalPlan=new Plan(plan.slots(),plan.users(),plan.posts(),skipped,plan.mutable(),plan.fixed(),plan.token());
        var result=new Receipt(VERSION,receipt.database(),receipt.base(),receipt.latest(),"COMPLETE",receipt.originalBase(),receipt.baseline(),receipt.source(),DemoDataSnapshot.capture(jdbc),ids,finalPlan);
        verify(result); write(path,result); return result;
    }
    private void insertAccount(long sourceId,Map<String,Map<Long,Long>> ids,String password) {
        var row=new TreeMap<>(latest.one("user",sourceId));
        if(ids.get("user").containsKey(sourceId)) throw new IllegalStateException("Refusing existing account replacement");
        row.put("password",context.getBean(PasswordEncoder.class).encode(password));
        put(ids,"user",sourceId,target.insert("user",row));
    }
    private void importReferences(Map<String,Map<Long,Long>> ids) {
        for(String table:List.of("dormitory","faculty","major")) for(var row:content.get(table)) {
            String name=table+"_name"; List<Map<String,Object>> candidates;
            if(table.equals("major")) candidates=target.query("SELECT * FROM major WHERE major_name=? AND faculty_id=?",row.get(name),lookup(ids,"faculty",row.get("faculty_id")));
            else candidates=target.query("SELECT * FROM `"+table+"` WHERE `"+name+"`=?",row.get(name));
            if(candidates.size()!=1 || !same(row.get("is_active"),candidates.getFirst().get("is_active"))) throw new IllegalStateException("Reference data mismatch: "+table);
            put(ids,table,id(table,row),id(table,candidates.getFirst()));
        }
    }
    private void importProfilesAndSurveys(Plan plan,Map<String,Map<Long,Long>> ids) {
        Set<Long> owners=new HashSet<>();
        for(var s:ExhibitionCatalog.students()) if(plan.slots().contains(s.slot())) owners.add(source.receipt().scenarios().get(s.key()));
        Long admin=source.receipt().scenarios().get(ExhibitionCatalog.MODERATOR_KEY);
        if(ids.get("user").containsKey(admin)) owners.add(admin);
        for(String table:List.of("user_profile","profile_survey","roommate_preference")) for(var row:content.get(table)) {
            long owner=number(row.get("user_id")); if(!owners.contains(owner)) continue;
            var converted=mapped(table,row,ids); long targetUser=number(converted.get("user_id"));
            var existing=target.query("SELECT * FROM `"+table+"` WHERE user_id=?",targetUser);
            long targetId;
            if(existing.isEmpty()) targetId=target.insert(table,converted);
            else {
                if(table.equals("roommate_preference")) throw new IllegalStateException("Unknown preference cannot be replaced");
                targetId=id(table,existing.getFirst()); target.update(table,targetId,converted);
            }
            put(ids,table,id(table,row),targetId);
        }
        // Preserve matching natural option IDs; remove only obsolete, fully verified legacy options.
        for(var sourceSurvey:content.get("profile_survey")) {
            Long targetSurvey=ids.getOrDefault("profile_survey",Map.of()).get(id("profile_survey",sourceSurvey)); if(targetSurvey==null) continue;
            var desired=content.get("profile_survey_option").stream().filter(o -> same(o.get("profile_survey_id"),id("profile_survey",sourceSurvey))).toList();
            for(var old:target.query("SELECT * FROM profile_survey_option WHERE profile_survey_id=?",targetSurvey)) {
                boolean keep=desired.stream().anyMatch(o -> same(o.get("field_code"),old.get("field_code")) && same(o.get("option_value"),old.get("option_value")));
                if(!keep) jdbc.update("DELETE FROM profile_survey_option WHERE profile_survey_option_id=?",id("profile_survey_option",old));
            }
            for(var row:desired) {
                var old=target.query("SELECT * FROM profile_survey_option WHERE profile_survey_id=? AND field_code=? AND option_value=?",targetSurvey,row.get("field_code"),row.get("option_value"));
                put(ids,"profile_survey_option",id("profile_survey_option",row),old.isEmpty()?target.insert("profile_survey_option",mapped("profile_survey_option",row,ids)):id("profile_survey_option",old.getFirst()));
            }
        }
        copyRows("roommate_preference_option",ids,new TreeMap<>()); copyRows("preference_criterion",ids,new TreeMap<>());
    }
    private void importComments(Map<String,Map<Long,Long>> ids,Map<String,String> skipped) {
        for(boolean reply:List.of(false,true)) for(var row:content.get("community_comment")) {
            if((row.get("parent_comment_id")!=null)!=reply) continue;
            var converted=tryMapped("community_comment",row,ids,skipped); if(converted==null) continue;
            if(truth(row.get("anonymous"))) context.getBean(CommunityAnonymousAuthorService.class).resolveNumbers(number(converted.get("post_id")),
                    context.getBean(UserRepository.class).findById(number(converted.get("author_id"))).orElseThrow());
            put(ids,"community_comment",id("community_comment",row),target.insert("community_comment",converted));
        }
    }
    private void importAliases(Map<String,Map<Long,Long>> ids,Map<String,String> skipped) {
        context.getBean(jakarta.persistence.EntityManager.class).flush();
        for(var row:content.get("community_anonymous_author")) {
            var converted=tryMapped("community_anonymous_author",row,ids,skipped); if(converted==null) continue;
            boolean hasImportedComment=content.get("community_comment").stream().anyMatch(c -> same(c.get("post_id"),row.get("post_id"))
                    && same(c.get("author_id"),row.get("author_id")) && truth(c.get("anonymous"))
                    && ids.getOrDefault("community_comment",Map.of()).containsKey(id("community_comment",c)));
            if(!hasImportedComment){skipped.put("community_anonymous_author/"+id("community_anonymous_author",row),"all anonymous comments for this identity were skipped");continue;}
            var actual=target.query("SELECT * FROM community_anonymous_author WHERE post_id=? AND author_id=?",converted.get("post_id"),converted.get("author_id"));
            if(actual.size()!=1) throw new IllegalStateException("Anonymous identity not resolved by existing service");
            put(ids,"community_anonymous_author",id("community_anonymous_author",row),id("community_anonymous_author",actual.getFirst()));
        }
    }
    private void importCategories(Map<String,Map<Long,Long>> ids,Map<String,String> skipped) {
        for(var row:content.get("calendar_category")) {
            var converted=tryMapped("calendar_category",row,ids,skipped); if(converted==null) continue;
            if(truth(row.get("is_system"))) {
                var existing=target.query("SELECT * FROM calendar_category WHERE category_code=? AND is_system=TRUE",row.get("category_code"));
                if(existing.size()!=1 || !same(existing.getFirst().get("default_name"),row.get("default_name"))) throw new IllegalStateException("System calendar category mismatch");
                put(ids,"calendar_category",id("calendar_category",row),id("calendar_category",existing.getFirst()));
            } else if(!target.query("SELECT category_id FROM calendar_category WHERE category_code=? OR (owner_user_id=? AND default_name=?)",row.get("category_code"),converted.get("owner_user_id"),row.get("default_name")).isEmpty())
                skipped.put("calendar_category/"+id("calendar_category",row),"unknown category collision; related events preserved/skipped");
            else put(ids,"calendar_category",id("calendar_category",row),target.insert("calendar_category",converted));
        }
    }
    private void importChats(Map<String,Map<Long,Long>> ids,Map<String,String> skipped) {
        context.getBean(jakarta.persistence.EntityManager.class).flush(); context.getBean(jakarta.persistence.EntityManager.class).clear();
        for(var row:content.get("chat_room")) {
            var converted=tryMapped("chat_room",row,ids,skipped); if(converted==null) continue;
            String key=roomKey(row,converted,ids);
            if(!target.query("SELECT chat_room_id FROM chat_room WHERE room_key=?",key).isEmpty()) {
                skipped.put("chat_room/"+id("chat_room",row),"existing unowned room key; never append messages"); continue;
            }
            converted.put("room_key",key); put(ids,"chat_room",id("chat_room",row),target.insert("chat_room",converted));
        }
    }
    private String roomKey(Map<String,Object> row,Map<String,Object> converted,Map<String,Map<Long,Long>> ids) {
        var members=content.get("chat_room_member").stream().filter(m -> same(m.get("chat_room_id"),id("chat_room",row))).toList();
        if(members.size()!=2) throw new IllegalStateException("Source room does not have exactly two participants");
        var post=converted.get("source_post_id")==null?null:context.getBean(CommunityPostRepository.class).findById(number(converted.get("source_post_id"))).orElseThrow();
        var comment=converted.get("source_comment_id")==null?null:context.getBean(CommunityCommentRepository.class).findById(number(converted.get("source_comment_id"))).orElseThrow();
        return ChatRoom.createRoomKey(lookup(ids,"user",members.get(0).get("user_id")),lookup(ids,"user",members.get(1).get("user_id")),ChatRoomCategory.valueOf(row.get("category").toString()),post,comment);
    }
    private void copyRows(String table,Map<String,Map<Long,Long>> ids,Map<String,String> skipped) {
        for(var row:content.get(table)) {
            var converted=tryMapped(table,row,ids,skipped); if(converted==null) continue;
            List<Map<String,Object>> existing=List.of();
            if(table.equals("post_like")) existing=target.query("SELECT * FROM post_like WHERE post_id=? AND user_id=?",converted.get("post_id"),converted.get("user_id"));
            if(table.equals("comment_like")) existing=target.query("SELECT * FROM comment_like WHERE comment_id=? AND user_id=?",converted.get("comment_id"),converted.get("user_id"));
            if(table.equals("report") && !target.query("SELECT report_id FROM report WHERE reporter_id=? AND report_type=? AND (post_id=? OR comment_id=?)",converted.get("reporter_id"),converted.get("report_type"),converted.get("post_id"),converted.get("comment_id")).isEmpty()) {
                skipped.put("report/"+id(table,row),"unowned report collision; no adoption"); continue;
            }
            put(ids,table,id(table,row),existing.isEmpty()?target.insert(table,converted):id(table,existing.getFirst()));
        }
    }
    private static void put(Map<String,Map<Long,Long>> ids,String table,long source,long target) { ids.computeIfAbsent(table,k -> new TreeMap<>()).put(source,target); }
    private static Long lookup(Map<String,Map<Long,Long>> ids,String table,Object value) {
        Long target=ids.getOrDefault(table,Map.of()).get(number(value));
        if(target==null) throw new IllegalStateException("Missing verified parent: "+table); return target;
    }
    private static Map<String,Object> mapped(String table,Map<String,Object> row,Map<String,Map<Long,Long>> ids) {
        var copy=new TreeMap<>(row);
        FK.getOrDefault(table,Map.of()).forEach((column,parent) -> {if(copy.get(column)!=null) copy.put(column,lookup(ids,parent,copy.get(column)));});
        return copy;
    }
    private static Map<String,Object> tryMapped(String table,Map<String,Object> row,Map<String,Map<Long,Long>> ids,Map<String,String> skipped) {
        for(var fk:FK.getOrDefault(table,Map.of()).entrySet()) if(row.get(fk.getKey())!=null && !ids.getOrDefault(fk.getValue(),Map.of()).containsKey(number(row.get(fk.getKey())))) {
            skipped.put(table+"/"+id(table,row),"parent scenario skipped: "+fk.getValue()); return null;
        }
        return mapped(table,row,ids);
    }
    private void assertProtected(Receipt receipt,Plan plan,DemoDataSnapshot after) {
        if(!receipt.baseline().tables().keySet().equals(after.tables().keySet())) throw new IllegalStateException("Table set changed");
        Set<String> append=new HashSet<>(FK.keySet()); append.addAll(Set.of("user","roommate_match")); append.remove("community_post");
        receipt.baseline().tables().forEach((table,rows) -> {
            if(!append.contains(table) && !rows.keySet().equals(after.tables().get(table).keySet()))
                throw new IllegalStateException("Unexpected added/deleted original data: "+table);
        });
        receipt.baseline().tables().forEach((table,rows) -> rows.forEach((key,hash) -> {
            if(!plan.mutable().getOrDefault(table,Set.of()).contains(key) && !hash.equals(after.tables().get(table).get(key)))
                throw new IllegalStateException("Protected original row changed: "+table);
        }));
        for(var table:plan.fixed().entrySet()) for(var original:table.getValue().entrySet()) {
            var row=target.one(table.getKey(),original.getKey()); Set<String> columns;
            if(table.getKey().equals("community_post")) {columns=new TreeSet<>(row.keySet());columns.removeAll(Set.of("title","content"));}
            else columns=Set.of(PK.get(table.getKey()),"user_id","created_at");
            var projected=new TreeMap<String,String>(); columns.forEach(c -> projected.put(c,row.get(c)==null?null:row.get(c).toString()));
            if(!original.getValue().equals(ExhibitionJournal.hash(projected))) throw new IllegalStateException("Immutable ownership/timestamp changed");
        }
    }
    public void verify(Receipt receipt) {
        requireReceipt(receipt);
        if(!receipt.status().equals("COMPLETE") || !DemoDataSnapshot.capture(jdbc).equals(receipt.completed())) throw new IllegalStateException("Merge is incomplete or changed");
        assertProtected(receipt,receipt.plan(),receipt.completed()); validateSource();
        new TransactionTemplate(context.getBean(PlatformTransactionManager.class)).executeWithoutResult(status -> {
            var ids=receipt.ids();
            for(var table:ids.entrySet()) {
                if(Set.of("user","dormitory","faculty","major","calendar_category").contains(table.getKey())) continue;
                for(var pair:table.getValue().entrySet()) {
                    String name=table.getKey(); var original=latest.one(name,pair.getKey()); var desired=mapped(name,original,ids); var actual=target.one(name,pair.getValue());
                    desired.remove(PK.get(name));
                    if(Set.of("user_profile","profile_survey").contains(name)) desired.remove("created_at");
                    if(name.equals("community_post")) desired.keySet().retainAll(Set.of("title","content","author_id","category","anonymous","status"));
                    if(name.equals("community_anonymous_author")) desired.remove("anonymous_number");
                    if(name.equals("chat_room")) desired.put("room_key",roomKey(original,desired,ids));
                    if(desired.entrySet().stream().anyMatch(e -> !same(e.getValue(),actual.get(e.getKey())))) throw new IllegalStateException("Imported data/FK mismatch: "+name);
                }
            }
            // Reuse the calculator verifier for only this cohort; protected rows also include all original account hashes.
            Map<String,Long> scenarios=new TreeMap<>();
            for(var s:ExhibitionCatalog.students()) if(receipt.plan().slots().contains(s.slot())) scenarios.put(s.key(),ids.get("user").get(source.receipt().scenarios().get(s.key())));
            if(scenarios.size()==50) {
                var snapshot=receipt.completed();
                new DemoDataExpansion(context).verify(new DemoDataExpansion.Receipt(receipt.database(),receipt.latest(),source.receipt().anchor(),"COMPLETE",DemoDataExpansion.catalog(),snapshot,snapshot,scenarios));
            }
        });
    }
}
