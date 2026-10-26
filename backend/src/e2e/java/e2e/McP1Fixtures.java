package e2e;

import java.time.*;
import java.util.*;
import jakarta.persistence.EntityManager;
import com.kkirikkiri.user.entity.*;
import com.kkirikkiri.calendar.entity.*;
import com.kkirikkiri.matching.entity.ProfileLike;

/** Independent synthetic P1 data. Never calls a collector or writes calculated match scores. */
final class McP1Fixtures {
    static final Map<String,Integer> COUNTS = Map.of("R07",5,"R08",7,"C05",5,"C06",4,"C07",10);
    static String caseId() {
        String id = E2eDatabase.required("E2E_MC_P1_CASE");
        E2eDatabase.check(id.matches("(R07|R08|C05|C06|C07)-[0-9]{2}"), "Invalid MC P1 fixture ID");
        E2eDatabase.check(Integer.parseInt(id.substring(4)) >= 1 &&
            Integer.parseInt(id.substring(4)) <= COUNTS.get(id.substring(0,3)), "Unknown MC P1 fixture ID");
        return id;
    }
    static void validateScope() {
        E2eDatabase.check(!"1".equals(System.getenv("E2E_MC_P0")) && !"1".equals(System.getenv("E2E_AUS_P1")) &&
            !E2eDatabase.retentionEnabled(), "Conflicting MC P1 fixture modes");
    }
    static void seed(EntityManager em, User a, User b, Dormitory dorm, Faculty faculty) throws Exception {
        validateScope(); String id = caseId();
        McFixtures.survey(em,a,false); McFixtures.survey(em,b,false);
        McFixtures.preferences(em,a,false); McFixtures.preferences(em,b,true);
        Long target = null;
        if (id.startsWith("R")) {
            var encoder = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
            for (int i=1;i<=12;i++) {
                var candidate = new User(encoder.encode(E2eDatabase.required("E2E_LOGIN_PASSWORD")),
                    "p1"+i+E2eDatabase.database().substring(10)+"@hknu.ac.kr");
                candidate.verifyEmail(); em.persist(candidate);
                var profile = new UserProfile(candidate,"P1후보"+String.format("%02d",i),"남자",dorm,faculty,null,(short)2000,"20","1학년","INTJ");
                profile.changeIntroduction("P1 합성 소개 "+i); em.persist(profile);
                McFixtures.survey(em,candidate,i>10);
                if (i==1) {
                    target = candidate.getUserId();
                    if (Set.of("R08-05","R08-07").contains(id)) em.persist(new ProfileLike(a,candidate));
                }
            }
        }
        var school = new CalendarCategory("SCHOOL","학교",null,true,true); em.persist(school);
        em.persist(new CalendarCategory("DORMITORY","기숙사",null,true,true));
        var personal = McFixtures.category(em,a,"CUSTOM-P1-A","P1개인",true);
        McFixtures.category(em,a,"CUSTOM-P1-EMPTY-A","P1빈A",true);
        McFixtures.category(em,a,"CUSTOM-P1-EMPTY-B","P1빈B",true);
        var other = McFixtures.category(em,a,"CUSTOM-P1-OTHER","P1다른달",true);
        McFixtures.category(em,b,"CUSTOM-P1-B","P1개인B",true);
        String from="2026-10-15",to=from,clock=from+"T12:00:00+09:00";
        switch (id) {
            case "C05-01" -> { from="2026-01-31"; to="2026-02-01"; }
            case "C05-02" -> { from="2028-02-28"; to="2028-03-01"; }
            case "C05-03" -> { from="2026-12-31"; to="2027-01-02"; }
            case "C05-04" -> { from="2026-12-31"; to="2027-01-01"; }
            case "C05-05" -> { from="2026-10-30"; to="2026-11-02"; }
        }
        clock=from+(id.equals("C05-04") ? "T23:59:00+09:00" : "T12:00:00+09:00");
        var ids = new LinkedHashMap<String,Long>();
        add(em,ids,a,personal,"P1기존",from,from,"09:00","10:00");
        add(em,ids,a,personal,"P1다음달","2026-11-15","2026-11-15","11:00","12:00");
        add(em,ids,a,other,"P1다른달참조","2026-11-15","2026-11-15",null,null);
        if (id.startsWith("C05")) {
            add(em,ids,a,personal,"P1다일",from,to,null,null);
            add(em,ids,a,personal,"P1끝날",to,to,"00:00","00:15");
            add(em,ids,a,personal,"P1이전",LocalDate.parse(from).minusDays(1).toString(),LocalDate.parse(from).minusDays(1).toString(),null,null);
            add(em,ids,a,personal,"P1이후",LocalDate.parse(to).plusDays(1).toString(),LocalDate.parse(to).plusDays(1).toString(),null,null);
            if (id.equals("C05-02")) add(em,ids,a,personal,"P1윤일","2028-02-29","2028-02-29",null,null);
            if (id.equals("C05-04") || id.equals("C05-05")) {
                String start = id.equals("C05-04") ? from : "2026-10-31";
                String end = LocalDate.parse(start).plusDays(1).toString();
                add(em,ids,a,personal,"P1자정",start,end,"23:45","00:15");
                add(em,ids,a,personal,"P1시작만",start,end,"09:15",null);
                add(em,ids,a,personal,"P1종료만",start,end,null,"10:15");
            }
        }
        add(em,ids,null,school,"P1공개",from,to,null,null);
        em.flush();
        var manifest = new LinkedHashMap<String,Object>();
        manifest.put("caseId",id); manifest.put("database",E2eDatabase.database()); manifest.put("userId",a.getUserId());
        manifest.put("targetId",target); manifest.put("from",from); manifest.put("to",to); manifest.put("clock",clock);
        manifest.put("events",ids);
        String json = tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(manifest);
        System.out.write(("E2E_MC_P1_FIXTURE "+json+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8)); System.out.flush();
    }
    static void add(EntityManager em, Map<String,Long> ids, User u, CalendarCategory c, String title,
                    String start, String end, String st, String et) {
        var event = new CalendarEvent(u,c,title,LocalDate.parse(start),LocalDate.parse(end),
            st==null?null:LocalTime.parse(st),et==null?null:LocalTime.parse(et),st==null&&et==null,
            "P1 원본 설명",u==null?CalendarEventSourceType.CRAWLED:CalendarEventSourceType.USER,u==null?"E2E 합성":null,null,null);
        em.persist(event); ids.put(title,event.getEventId());
    }
}
