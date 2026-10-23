package kmi;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import com.sun.net.httpserver.HttpServer;
import tools.jackson.databind.JsonNode;
import static kmi.MatchingMultiSupport.*;
import static com.kkirikkiri.survey.SurveyTestData.*;

/** Additional cases only. Reuses the prior actual TCP/server/JPA/cleanup harness. */
public class MatchingHttp5 extends MatchingMultiInstance {
    static final int PORT=18095;
    static final String REC="/api/roommates/recommended";
    static final List<String> CATALOG=List.of("recommended-59-60-order-page", "recommended-compound-filter", "rank-nine-no-backfill", "filtered-pagination", "weights-refresh-recommend-detail", "ai-local-score-consistency", "ai-local-malformed-recovery", "ai-local-upstream-error", "query-repeated-comma", "query-blank-too-long", "unsupported-sort", "no-preference-score-null", "all-indifferent-score-null", "rounding-boundaries");
    static List<Long> tied;
    public static void main(String[] args)throws Exception {
        if(args.length==1&&args[0].equals("--list")){System.out.println(JSON.writeValueAsString(CATALOG));return;}
        check("true".equals(System.getenv("MC5_APPROVED")),"Explicit approved isolated execution required");
        output=Path.of(args[0]).toRealPath();String priority=args[1];check(Set.of("P0","P1").contains(priority),"Unknown priority");boolean completed=false,cleaned=false;Map<String,String> originalDdl=null;
        var stub=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var workers=Executors.newVirtualThreadPerTaskExecutor();stub.setExecutor(workers);
        stub.createContext("/chat/completions/",x->{stubCalls.incrementAndGet();try{
            check(x.getRequestMethod().equals("POST"),"Gateway method");var request=JSON.readTree(x.getRequestBody());
            var input=JSON.readTree(request.path("messages").get(1).path("content").asString());
            check(input.properties().stream().map(Map.Entry::getKey).collect(java.util.stream.Collectors.toSet()).equals(Set.of("matchScore","matchedItems","mismatchedItems")),"Unexpected AI input fields");
            int status=stubMode.equals("unavailable")?503:200;
            String body=stubMode.equals("invalid")?"{\"choices\":[]}":JSON.writeValueAsString(Map.of("choices",List.of(Map.of("message",Map.of("content","로컬 계약 확인. 점수 999는 설명 문자열입니다.")))));
            byte[] bytes=body.getBytes(StandardCharsets.UTF_8);x.getResponseHeaders().set("Content-Type","application/json");x.sendResponseHeaders(status,bytes.length);x.getResponseBody().write(bytes);
            events.add(Map.of("kind","localGatewayInput","score",input.path("matchScore").asInt(),"mode",stubMode));
        }catch(Throwable t){events.add(Map.of("kind","stubError","error",error(t)));}finally{x.close();}});
        try {
            if(priority.equals("P0")&&!"true".equals(System.getenv("MC5_EXISTING_SCHEMA")))initialize();else existingSchema();
            originalDdl=ddl();write(output.resolve("schema-before.json"),originalDdl);
            stub.start();a=start(PORT,stub.getAddress().getPort());fixtures();
            // Ten tied candidates at 60, one at 59; remaining candidates are ineligible fixtures.
            for(int i=12;i<15;i++)update("UPDATE user SET status='SUSPENDED' WHERE user_id=?",members.get(i).id());
            tied=members.subList(1,11).stream().map(Member::id).toList();
            expect(api(PORT,"PUT",SURVEY,change(profile(false),"sleepTime","23시 ~ 24시",com.kkirikkiri.survey.dto.ProfileSurveyRequest.class),members.get(11)),200,"");
            save(threshold());awaitScore(target,60);awaitScore(members.get(11),59);
            if(priority.equals("P0"))p0();else p1();completed=true;
        }catch(Throwable t){cases.add(Map.of("name","harness","result","INCOMPLETE","error",error(t)));}
        finally {
            for(var s:servers)try{s.stop();}catch(Throwable t){events.add(Map.of("kind","stopFailure","error",error(t)));}
            stub.stop(0);workers.shutdownNow();HTTP.close();if(emf!=null)emf.close();
            boolean stopped=servers.stream().noneMatch(s->s.process.isAlive());
            if(stopped)try{cleanup();cleaned=true;}catch(Throwable t){events.add(Map.of("kind","cleanupFailure","error",error(t)));}
            boolean unchanged=false;try{var after=ddl();write(output.resolve("schema-after.json"),after);unchanged=Objects.equals(originalDdl,after);}catch(Throwable t){events.add(Map.of("kind","ddlAuditFailure","error",error(t)));}
            write(output.resolve("cases.json"),cases);write(output.resolve("requests.json"),requests);write(output.resolve("servers.json"),events);
            boolean passed=completed&&cleaned&&stopped&&unchanged&&cases.size()==(priority.equals("P0")?8:6)&&cases.stream().allMatch(c->"PASS".equals(c.get("result")))&&events.stream().noneMatch(e->Set.of("stubError","forbiddenMail").contains(e.get("kind")));
            var summary=new LinkedHashMap<String,Object>();summary.put("priority",priority);summary.put("verdict",passed?"PASS":"FAIL_OR_INCOMPLETE");summary.put("scenarios",cases.size());summary.put("passed",cases.stream().filter(c->"PASS".equals(c.get("result"))).count());summary.put("httpRequests",requests.size());summary.put("serverHttpObserved",events.stream().filter(e->"http".equals(e.get("kind"))).count());summary.put("processesStopped",stopped);summary.put("allTablesEmpty",cleaned);summary.put("ddlUnchanged",unchanged);summary.put("externalGatewayCalls",0);summary.put("localStubCalls",stubCalls.get());write(output.resolve("summary.json"),summary);
            if(!passed)System.exit(1);
        }
    }
    static Map<String,String> ddl()throws Exception{var result=new TreeMap<String,String>();try(var c=open()){for(var t:rows(c,"SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() ORDER BY table_name")){String name=t.values().iterator().next().toString();check(name.matches("[a-z_]+"),"Unexpected table");result.put(name,rows(c,"SHOW CREATE TABLE `"+name+"`").getFirst().values().stream().skip(1).findFirst().orElseThrow().toString().replaceAll(" AUTO_INCREMENT=\\d+",""));}}return result;}
    static void existingSchema()throws Exception{
        try(var c=open()){var tables=rows(c,"SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE()");check(tables.size()==31,"Expected retained initialized schema");for(var t:tables){String name=t.values().iterator().next().toString();check(name.matches("[a-z_]+"),"Unexpected table");check(number(c,"SELECT COUNT(*) FROM `"+name+"`")==0,"Previous phase left rows");}write(output.resolve("environment.json"),rows(c,"SELECT VERSION() version,DATABASE() db,@@server_uuid uuid,@@hostname hostname,@@transaction_isolation isolationLevel,@@session.time_zone zone"));write(output.resolve("grants.json"),rows(c,"SHOW GRANTS"));}
        var f=new LocalContainerEntityManagerFactoryBean();f.setDataSource(source());f.setPackagesToScan("com.kkirikkiri");f.setJpaVendorAdapter(new HibernateJpaVendorAdapter());f.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","validate","hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl"));f.afterPropertiesSet();emf=f.getObject();
    }
    static Map<String,Object> preference(Map<String,Object> choices,Map<String,Integer> overrides){
        var v=new LinkedHashMap<String,Object>(JSON.convertValue(com.kkirikkiri.survey.SurveyTestData.preference(false),Map.class));
        v.replaceAll((k,x)->x instanceof List<?>?List.of("상관없음"):x instanceof String?"상관없음":x);
        var w=weights(5);w.putAll(overrides);v.putAll(choices);v.put("weights",w);return v;
    }
    static Map<String,Object> threshold(){return preference(Map.ofEntries(Map.entry("bedtime",List.of("22시 이전")),Map.entry("wakeTime",List.of("07시 이전")),Map.entry("sleepTalking","없음만 가능"),Map.entry("snoring","적음까지 괜찮음"),Map.entry("teethGrinding","보통까지 괜찮음"),Map.entry("smoking","비흡연자만"),Map.entry("showerTime","저녁"),Map.entry("showerDuration","20분 이하"),Map.entry("department","같은 전공"),Map.entry("age",List.of("연하")),Map.entry("grade",List.of("4학년")),Map.entry("cleaningFrequency",List.of("매일")),Map.entry("cleaningStyle","각자 영역만 청소하는 사람")),Map.of("bedtime",1,"showerDuration",4,"cleaningFrequency",4,"cleaningStyle",4));}
    static void save(Object v)throws Exception{expect(api(PORT,"PUT",PREFERENCE,v,me),200,"");}
    static void awaitScore(Member m,int expected)throws Exception{
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);
        while(n("SELECT COUNT(*) FROM roommate_match WHERE requester_user_id=? AND target_user_id=? AND match_score=?",me.id(),m.id(),expected)!=1){check(System.nanoTime()<until,"Committed async score did not reach "+expected);Thread.sleep(25);}
    }
    static JsonNode rec(String q)throws Exception{var r=api(PORT,"GET",REC+q,null,me);expect(r,200,"");return r.data();}
    static List<Long> ids(JsonNode data){var ids=new ArrayList<Long>();for(var v:data.path("content"))ids.add(v.path("id").asLong());return ids;}
    static String enc(String v){return URLEncoder.encode(v,StandardCharsets.UTF_8);}
    static int score(Member m)throws Exception{var r=api(PORT,"GET","/api/roommates/"+m.id(),null,me);expect(r,200,"");return r.data().path("matchScore").asInt();}
    static void p0(){
        test(CATALOG.get(0),"60 included; 59 excluded; score DESC/id ASC; size 10 clamps to 9; second page retains rank ten",()->{
            check(score(members.get(11))==59,"59 fixture");for(int size:List.of(9,10)){var d=rec("?size="+size+"&sort=matchScore,desc");check(ids(d).equals(tied.subList(0,9))&&d.path("size").asInt()==9&&d.path("totalElements").asInt()==10&&d.path("totalPages").asInt()==2,"Threshold/order/page mismatch");for(var v:d.path("content"))check(v.path("matchScore").asInt()==60,"Non-60 result");}
            check(ids(rec("?page=1&size=10")).equals(tied.subList(9,10)),"Second page mismatch");return Map.of("eligible",10,"includedScore",60,"excludedScore",59,"firstNine",tied.subList(0,9));});
        test(CATALOG.get(1),"AND across scalar and multi filters with exact ranked IDs",()->{
            String q="?smoking="+enc("비흡연")+"&dormStayDays="+enc("월")+"&mealPlan="+enc("석식");check(ids(rec(q)).equals(tied.subList(0,9)),"Compound filter mismatch");return Map.of("expectedIds",tied.subList(0,9));});
        test(CATALOG.get(2),"Rank ten matches a filter, but cannot backfill a filtered top nine",()->{
            expect(api(PORT,"PUT",SURVEY,change(profile(false),"dormStayDays",List.of("토","일"),com.kkirikkiri.survey.dto.ProfileSurveyRequest.class),members.get(10)),200,"");idle();
            check(score(members.get(10))==60,"Filter-only fixture changed score");check(ids(rec("?dormStayDays="+enc("토"))).isEmpty(),"Backfilled rank ten");return Map.of("rankTen",members.get(10).id(),"rankTenScore",60,"returned",0);});
        test(CATALOG.get(3),"Filtered total/pages apply after top-nine selection; disjoint pages concatenate exactly",()->{var all=new ArrayList<Long>();for(int page=0;page<3;page++){var d=rec("?smoking="+enc("비흡연")+"&page="+page+"&size=4");check(d.path("totalElements").asInt()==9&&d.path("totalPages").asInt()==3,"Filtered count");all.addAll(ids(d));}check(all.equals(tied.subList(0,9)),"Filtered pagination");return Map.of("ids",all);});
        test(CATALOG.get(4),"HTTP weight reversal yields committed 17 then 83 without repairing GET; recommendation/detail agree",()->{
            var choices=Map.<String,Object>of("bedtime",List.of("23시 ~ 24시"),"wakeTime",List.of("07시 이전"));
            save(preference(choices,Map.of("bedtime",5,"wakeTime",1)));awaitScore(target,17);check(score(target)==17&&!ids(rec("")).contains(target.id()),"Low score reads");
            save(preference(choices,Map.of("bedtime",1,"wakeTime",5)));awaitScore(target,83);check(score(target)==83&&ids(rec("")).contains(target.id()),"High score reads");return Map.of("before",17,"after",83);});
        test(CATALOG.get(5),"Real Gateway client uses local TCP stub; API score stays 83 even when explanation mentions 999",()->{resetQuota();var r=api(PORT,"POST",ai(),null,me);expect(r,200,"");check(r.data().path("matchScore").asInt()==score(target)&&r.data().path("matchScore").asInt()==83&&r.data().path("targetUserId").asLong()==target.id(),"AI score mismatch");check(events.stream().anyMatch(e->"localGatewayInput".equals(e.get("kind"))&&Integer.valueOf(83).equals(e.get("score"))),"Gateway input score missing");return Map.of("score",83,"transport","LOCAL_STUB_ONLY","externalGatewayVerified",false);});
        test(CATALOG.get(6),"Malformed local upstream maps to 502 and next request recovers; matching data preserved",()->{resetQuota();stubMode="invalid";expect(api(PORT,"POST",ai(),null,me),502,"AI_GATEWAY_INVALID_RESPONSE");stubMode="normal";expect(api(PORT,"POST",ai(),null,me),200,"");check(score(target)==83,"Error changed matching");return Map.of("errorCode","AI_GATEWAY_INVALID_RESPONSE","recovered",true);});
        test(CATALOG.get(7),"Local upstream 503 maps to 503 AI_GATEWAY_UNAVAILABLE",()->{resetQuota();try{stubMode="unavailable";var r=api(PORT,"POST",ai(),null,me);expect(r,503,"AI_GATEWAY_UNAVAILABLE");check(r.data().isNull(),"Error data must be null");return Map.of("errorCode",r.code());}finally{stubMode="normal";}});
    }
    static void p1(){
        test(CATALOG.get(8),"Repeated query values and comma-separated values have identical order/counts",()->{var repeat=rec("?dormStayDays="+enc("월")+"&dormStayDays="+enc("토"));var comma=rec("?dormStayDays="+enc("월,토"));check(repeat.equals(comma),"Query encodings differ");return Map.of("ids",ids(repeat));});
        test(CATALOG.get(9),"Blank and 31-char smoking filter rejected; unknown nonblank option returns empty",()->{for(String v:List.of(" ","x".repeat(31)))expect(api(PORT,"GET",REC+"?smoking="+enc(v),null,me),400,"VALIDATION_ERROR");check(ids(rec("?smoking=unsupported")).isEmpty(),"Unknown filter not empty");return Map.of("invalid",2,"unknownEmpty",true);});
        test(CATALOG.get(10),"Unsupported recommended sort retains INVALID_REQUEST",()->{for(String sort:List.of("matchScore,asc","userId,asc","nickname,desc"))expect(api(PORT,"GET",REC+"?sort="+sort,null,me),400,"INVALID_REQUEST");return Map.of("rejected",3);});
        test(CATALOG.get(11),"No requester preference gives null detail score, empty recommendations and 409 analysis without Gateway",()->{
            var noPreference=members.get(2);int calls=stubCalls.get();var r=api(PORT,"GET",detail(),null,noPreference);expect(r,200,"");check(r.data().path("matchScore").isNull(),"Missing preference score");r=api(PORT,"GET",REC,null,noPreference);expect(r,200,"");check(ids(r.data()).isEmpty(),"Missing preference recommended");expect(api(PORT,"POST",ai(),null,noPreference),409,"MATCH_ANALYSIS_UNAVAILABLE");check(calls==stubCalls.get(),"Uncomputable reached Gateway");return Map.of("gatewayCalls",0);});
        test(CATALOG.get(12),"All indifferent criteria gives null score and empty recommendations",()->{save(preference(Map.of(),Map.of()));idle();var r=api(PORT,"GET",detail(),null,me);expect(r,200,"");check(r.data().path("matchScore").isNull()&&ids(rec("")).isEmpty(),"Zero denominator fabricated score");return Map.of("scoreIsNull",true);});
        test(CATALOG.get(13),"Partial weights round 14.285→14,12.5→13,16.666→17",()->{var result=new ArrayList<Integer>();for(int[] v:new int[][]{{3,3,14},{3,4,13},{2,3,17}}){save(preference(Map.of("bedtime",List.of("23시 ~ 24시"),"wakeTime",List.of("07시 이전"),"grade",List.of("4학년")),Map.of("bedtime",v[0],"wakeTime",1,"grade",v[1])));awaitScore(target,v[2]);check(score(target)==v[2],"Rounding mismatch");result.add(v[2]);}return result;});
    }
}
