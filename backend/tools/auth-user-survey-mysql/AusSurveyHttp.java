import java.util.*;
import static com.kkirikkiri.survey.SurveyTestData.*;

/** Missing real HTTP/MySQL survey paths, using the existing server, JWT and SQL observers. */
class AusSurveyHttp extends UserMultiInstance {
    static final String PROFILE="/api/users/me/profile-survey",PREFERENCE="/api/users/me/roommate-preferences";
    static void run()throws Exception {
        if(!selected.isEmpty())return;
        test("survey-http-profile-roundtrip-owner-and-introduction",()->roundtrip(false));
        test("survey-http-preference-roundtrip-options-and-weights",()->roundtrip(true));
        test("survey-http-profile-invalid-input-preserves-all-rows",()->invalid(false));
        test("survey-http-preference-invalid-input-preserves-all-rows",()->invalid(true));
        test("survey-http-missing-and-inactive-authentication",AusSurveyHttp::authentication);
        test("survey-mysql-physical-pk-fk-unique-notnull-and-rollback",AusSurveyHttp::constraints);
    }
    static Map<String,Object> body(boolean preference,boolean second){return new LinkedHashMap<>(JSON.mapper.convertValue(preference?com.kkirikkiri.survey.SurveyTestData.preference(second):com.kkirikkiri.survey.SurveyTestData.profile(second),Map.class));}
    static Map<String,Object> roundtrip(boolean preference)throws Exception {
        Member owner=member(true),other=member(true);String path=preference?PREFERENCE:PROFILE;
        var initial=api(18091,"GET",path,null,owner.jwt());expect(initial,200,"");UserMultiSupport.check(!initial.json().path("data").path("completed").asBoolean()&&initial.json().path("data").path("answers").isNull(),"Missing survey contract changed");
        long parent=0;
        for(boolean second:List.of(false,true)){
            var request=body(preference,second);request.put("userId",other.id());
            expect(api(18091,"PUT",path,request,owner.jwt()),200,"");
            var saved=api(18092,"GET",path,null,owner.jwt());expect(saved,200,"");
            var data=saved.json().path("data");UserMultiSupport.check(data.path("completed").asBoolean(),"Saved survey incomplete");
            var expected=body(preference,second);Object intro=expected.remove("introduction");
            UserMultiSupport.check(JSON.mapper.valueToTree(expected).equals(data.path("answers")),"Saved answers differ from canonical request");
            if(!preference)UserMultiSupport.check(Objects.equals(intro,data.path("introduction").asString()),"Introduction not returned");
            String table=preference?"roommate_preference":"profile_survey",id=preference?"preference_id":"profile_survey_id";
            long current=query("SELECT "+id+" FROM "+table+" WHERE user_id=?",owner.id());
            if(parent!=0)UserMultiSupport.check(parent==current,"Repeated save changed parent identity");parent=current;
            UserMultiSupport.check(query("SELECT COUNT(*) FROM "+table+" WHERE user_id=?",other.id())==0,"Forged owner changed another user");
            UserMultiSupport.check(query(preference?"SELECT COUNT(*) FROM roommate_preference_option WHERE preference_id=?":"SELECT COUNT(*) FROM profile_survey_option WHERE profile_survey_id=?",parent)==(preference?(second?6:7):(second?3:4)),"Old child options survived replacement");
            if(preference)UserMultiSupport.check(query("SELECT COUNT(*) FROM preference_criterion WHERE preference_id=? AND weight=?",parent,second?5:2)==18,"Weights not fully replaced");
        }
        if(!preference){
            var legacy=body(false,false);legacy.remove("introduction");
            expect(api(18091,"PUT",path,legacy,owner.jwt()),200,"");
            UserMultiSupport.check(api(18092,"GET",path,null,owner.jwt()).json().path("data").path("introduction").asString().equals("수정 소개"),"Omitted introduction erased text");
            legacy.put("introduction"," \n\t\u00a0\u3000\ufeff");expect(api(18091,"PUT",path,legacy,owner.jwt()),200,"");
            try(var c=UserMultiSupport.open()){UserMultiSupport.check(UserMultiSupport.rows(c,"SELECT introduction FROM user_profile WHERE user_id=?",owner.id()).getFirst().get("introduction").equals(""),"Blank introduction not cleared");}
        }
        return Map.of("realHttp",true,"separateConnectionRead",true,"parentReused",true,"ownerIsolated",true);
    }
    static Object snapshot(Member u)throws Exception {
        try(var c=UserMultiSupport.open()){return List.of(
            UserMultiSupport.rows(c,"SELECT * FROM profile_survey WHERE user_id=?",u.id()),
            UserMultiSupport.rows(c,"SELECT o.* FROM profile_survey_option o JOIN profile_survey p ON p.profile_survey_id=o.profile_survey_id WHERE p.user_id=? ORDER BY o.profile_survey_option_id",u.id()),
            UserMultiSupport.rows(c,"SELECT * FROM roommate_preference WHERE user_id=?",u.id()),
            UserMultiSupport.rows(c,"SELECT o.* FROM roommate_preference_option o JOIN roommate_preference p ON p.preference_id=o.preference_id WHERE p.user_id=? ORDER BY o.preference_option_id",u.id()),
            UserMultiSupport.rows(c,"SELECT w.* FROM preference_criterion w JOIN roommate_preference p ON p.preference_id=w.preference_id WHERE p.user_id=? ORDER BY w.preference_criterion_id",u.id()),
            UserMultiSupport.rows(c,"SELECT introduction FROM user_profile WHERE user_id=?",u.id()));}
    }
    static Map<String,Object> invalid(boolean preference)throws Exception {
        Member u=member(true);String path=preference?PREFERENCE:PROFILE;
        expect(api(18091,"PUT",path,body(preference,false),u.jwt()),200,"");Object before=snapshot(u);
        var requests=new ArrayList<Map<String,Object>>();
        if(preference){
            var request=body(true,true);request.put("age",List.of("상관없음","동갑"));requests.add(request);
            request=body(true,true);var wrong=weights(3);wrong.put("unsupported",wrong.remove("meal"));request.put("weights",wrong);requests.add(request);
        }else{
            var request=body(false,true);request.put("meal",null);requests.add(request);
            request=body(false,true);request.put("mealPlan",List.of("신청 안 함","조식"));requests.add(request);
            request=body(false,true);request.put("introduction","가".repeat(201));requests.add(request);
        }
        for(var request:requests){expect(api(18091,"PUT",path,request,u.jwt()),400,"VALIDATION_ERROR");UserMultiSupport.check(before.equals(snapshot(u)),"Rejected request changed persistent rows");}
        return Map.of("rejectedRequests",requests.size(),"allStoredRowsPreserved",true);
    }
    static Map<String,Object> authentication()throws Exception {
        Member u=member(true);
        for(String path:List.of(PROFILE,PREFERENCE)){
            expect(api(18091,"GET",path,null,""),401,"UNAUTHORIZED");
            expect(api(18091,"PUT",path,body(path.equals(PREFERENCE),false),""),401,"UNAUTHORIZED");
        }
        for(String state:List.of("SUSPENDED","WITHDRAWN")){
            Member inactive=member(true);expect(status(18091,inactive,state),200,"");Object before=snapshot(inactive);
            for(String path:List.of(PROFILE,PREFERENCE)){
                expect(api(18092,"GET",path,null,inactive.jwt()),401,"UNAUTHORIZED");
                expect(api(18092,"PUT",path,body(path.equals(PREFERENCE),false),inactive.jwt()),401,"UNAUTHORIZED");
            }
            UserMultiSupport.check(before.equals(snapshot(inactive)),"Inactive account wrote survey");
        }
        return Map.of("missingJwtBlocked",true,"suspendedAndWithdrawnBlocked",true);
    }
    interface SqlAction {void run()throws java.sql.SQLException;}
    static void sqlError(int code,SqlAction action)throws Exception {
        try{action.run();throw new AssertionError("Expected physical MySQL constraint rejection");}
        catch(java.sql.SQLException failure){UserMultiSupport.check(failure.getErrorCode()==code,"Unexpected physical constraint vendor code: "+failure.getErrorCode());}
    }
    static Map<String,Object> constraints()throws Exception {
        Member u=member(true);
        expect(api(18091,"PUT",PROFILE,body(false,false),u.jwt()),200,"");
        expect(api(18091,"PUT",PREFERENCE,body(true,false),u.jwt()),200,"");
        Object before=snapshot(u);int rejected=0;
        try(var c=UserMultiSupport.open()){
            c.setAutoCommit(false);
            try{
                UserMultiSupport.exec(c,"UPDATE user_profile SET introduction='constraint-transaction-must-rollback' WHERE user_id=?",u.id());
                for(String table:List.of("profile_survey","roommate_preference")){
                    var columns=UserMultiSupport.rows(c,"SELECT column_name AS col,column_key AS key_kind FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=? ORDER BY ordinal_position",table);
                    var all=new ArrayList<String>();var nonPk=new ArrayList<String>();
                    for(var col:columns){String name=col.get("col").toString();UserMultiSupport.check(name.matches("[a-z_]+"),"Unsafe metadata column");all.add(name);if(!col.get("key_kind").equals("PRI"))nonPk.add(name);}
                    String allColumns=String.join(",",all),writable=String.join(",",nonPk);
                    sqlError(1062,()->UserMultiSupport.exec(c,"INSERT INTO "+table+" ("+allColumns+") SELECT "+allColumns+" FROM "+table+" WHERE user_id=?",u.id()));rejected++;
                    sqlError(1062,()->UserMultiSupport.exec(c,"INSERT INTO "+table+" ("+writable+") SELECT "+writable+" FROM "+table+" WHERE user_id=?",u.id()));rejected++;
                    String missingOwner=String.join(",",nonPk.stream().map(x->x.equals("user_id")?"-1":x).toList());
                    sqlError(1452,()->UserMultiSupport.exec(c,"INSERT INTO "+table+" ("+writable+") SELECT "+missingOwner+" FROM "+table+" WHERE user_id=?",u.id()));rejected++;
                }
                for(String table:List.of("profile_survey_option","roommate_preference_option","preference_criterion")){
                    String fk=table.equals("profile_survey_option")?"profile_survey_id":"preference_id";
                    var columns=UserMultiSupport.rows(c,"SELECT column_name AS col FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=? AND column_key<>'PRI' ORDER BY ordinal_position",table);
                    var names=columns.stream().map(x->x.get("col").toString()).toList();String joined=String.join(",",names);
                    sqlError(1062,()->UserMultiSupport.exec(c,"INSERT INTO "+table+" ("+joined+") SELECT "+joined+" FROM "+table+" LIMIT 1"));rejected++;
                    String orphan=String.join(",",names.stream().map(x->x.equals(fk)?"-1":x).toList());
                    sqlError(1452,()->UserMultiSupport.exec(c,"INSERT INTO "+table+" ("+joined+") SELECT "+orphan+" FROM "+table+" LIMIT 1"));rejected++;
                    String nullFk=String.join(",",names.stream().map(x->x.equals(fk)?"NULL":x).toList());
                    sqlError(1048,()->UserMultiSupport.exec(c,"INSERT INTO "+table+" ("+joined+") SELECT "+nullFk+" FROM "+table+" LIMIT 1"));rejected++;
                }
            }finally{c.rollback();}
        }
        UserMultiSupport.check(before.equals(snapshot(u)),"Constraint transaction partially persisted");
        return Map.of("constraintRejections",rejected,"mysqlCodes",List.of(1062,1452,1048),"entireTransactionRolledBack",true);
    }
}
