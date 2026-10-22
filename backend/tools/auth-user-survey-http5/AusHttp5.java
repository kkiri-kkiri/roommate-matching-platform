import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static com.kkirikkiri.survey.SurveyTestData.*;

/** Only missing HTTP cases. Reuses the prior Boot server, real JWT/mail capture and JDBC guards. */
public class AusHttp5 extends UserMultiInstance {
    static final int PORT=18093;
    static final String PROFILE="/api/users/me/profile-survey",PREFERENCE="/api/users/me/roommate-preferences";
    record Scenario(String name,String priority,String expected,Work action){}
    static final List<Scenario> PLAN=new ArrayList<>();
    static Map<String,String> sourceHashes()throws Exception{
        var hashes=new TreeMap<>(UserMultiInstance.sourceHashes());
        for(String dir:List.of("backend/tools/user-mysql-multi-instance","backend/tools/auth-user-survey-mysql","backend/tools/auth-user-survey-http5","backend/validation")){
            try(var files=Files.walk(root.resolve(dir))){for(var f:files.filter(Files::isRegularFile).sorted().toList())
                hashes.put(root.relativize(f).toString(),HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(f))));}
        }
        Path jar=root.resolve("backend/build/libs/backend-0.0.1-SNAPSHOT.jar");
        hashes.put(root.relativize(jar).toString(),HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar))));return hashes;
    }
    static void add(String name,String priority,String expected,Work action){PLAN.add(new Scenario(name,priority,expected,action));}
    static Map<String,Object> ok(){return Map.of("realTcpHttp",true,"separateJdbcAssertions",true);}
    static void check(boolean value,String message){UserMultiSupport.check(value,message);}
    static void exec(String sql,Object...args)throws Exception{try(var c=UserMultiSupport.open()){UserMultiSupport.exec(c,sql,args);}}
    static void expire(String table,String column,String ownerColumn,Object owner)throws Exception{
        check(Set.of("email_verification","password_reset_token").contains(table)&&Set.of("expires_at","verification_token_expires_at").contains(column)&&Set.of("email","user_id").contains(ownerColumn),"Unapproved expiration fixture column");
        // A fixed past second avoids DATETIME(0) fractional rounding and clock-boundary ambiguity.
        LocalDateTime target=LocalDateTime.of(2000,1,1,0,0,0);
        try(var c=UserMultiSupport.open()){
            // Match Hibernate's LocalDateTime -> Timestamp binding on this UTC JDBC connection.
            int changed;
            try(var update=c.prepareStatement("UPDATE "+table+" SET "+column+"=? WHERE "+ownerColumn+"=?")){
                update.setTimestamp(1,java.sql.Timestamp.valueOf(target));update.setObject(2,owner);changed=update.executeUpdate();
            }
            check(changed==1,"Expiration fixture must change exactly one owned row");
            try(var s=c.prepareStatement("SELECT "+column+" FROM "+table+" WHERE "+ownerColumn+"=?")){
                s.setObject(1,owner);try(var r=s.executeQuery()){
                    check(r.next(),"Expiration fixture row absent");
                    var effective=org.hibernate.type.descriptor.java.LocalDateTimeJavaType.INSTANCE.wrap(r.getTimestamp(1),null);
                    boolean expired=effective.isBefore(LocalDateTime.now(ZoneId.of("Asia/Seoul")));
                    UserMultiSupport.write(out.resolve(activeCase+"-expiration.json"),Map.of("case",activeCase,"method","adjusted fixture timestamp; not real TTL elapsed","table",table,"column",column,"rowsChanged",changed,"requestedApplicationTime",target.toString(),"effectiveApplicationTime",effective.toString(),"storedDatabaseLiteral",r.getString(1),"expiredBeforeHttp",expired));
                    check(expired,"Fixture is not expired under Hibernate's application mapping");
                }
            }
        }
    }
    static Reply call(String method,String path,Object body,String token)throws Exception{return api(PORT,method,path,body,token);}
    static Reply auth(String path,Object body)throws Exception{return call("POST","/api/auth/"+path,body,"");}
    static Map<String,Object> surveyBody(boolean preference){return new LinkedHashMap<>(JSON.mapper.convertValue(preference?com.kkirikkiri.survey.SurveyTestData.preference(false):com.kkirikkiri.survey.SurveyTestData.profile(false),Map.class));}
    static Object surveySnapshot(Member member)throws Exception{
        try(var c=UserMultiSupport.open()){
            var result=new ArrayList<Object>();
            result.add(UserMultiSupport.rows(c,"SELECT * FROM user_profile WHERE user_id=?",member.id()));
            for(String table:List.of("profile_survey","roommate_preference"))result.add(UserMultiSupport.rows(c,"SELECT * FROM "+table+" WHERE user_id=?",member.id()));
            result.add(UserMultiSupport.rows(c,"SELECT o.* FROM profile_survey_option o JOIN profile_survey p ON p.profile_survey_id=o.profile_survey_id WHERE p.user_id=? ORDER BY o.profile_survey_option_id",member.id()));
            result.add(UserMultiSupport.rows(c,"SELECT o.* FROM roommate_preference_option o JOIN roommate_preference p ON p.preference_id=o.preference_id WHERE p.user_id=? ORDER BY o.preference_option_id",member.id()));
            result.add(UserMultiSupport.rows(c,"SELECT w.* FROM preference_criterion w JOIN roommate_preference p ON p.preference_id=w.preference_id WHERE p.user_id=? ORDER BY w.preference_criterion_id",member.id()));
            return result;
        }
    }
    static Object verificationSnapshot(String email)throws Exception{
        try(var c=UserMultiSupport.open()){return UserMultiSupport.rows(c,"SELECT * FROM email_verification WHERE email=? ORDER BY verification_id",email);}
    }
    static Object accountSnapshot(Member member)throws Exception{
        try(var c=UserMultiSupport.open()){return List.of(UserMultiSupport.rows(c,"SELECT * FROM user WHERE user_id=?",member.id()),UserMultiSupport.rows(c,"SELECT * FROM password_reset_token WHERE user_id=? ORDER BY password_reset_token_id",member.id()));}
    }
    static String signupToken(String email)throws Exception{
        expect(auth("email-verifications/send",Map.of("email",email,"purpose","SIGNUP")),200,"");
        var r=auth("email-verifications/verify",Map.of("email",email,"purpose","SIGNUP","code",code(email)));
        expect(r,200,"");String token=r.json().path("data").path("verificationToken").asString();
        check(!token.isBlank(),"Verification token absent");return token;
    }
    static Map<String,Object> signupBody(String email,String token,String password){
        var body=new LinkedHashMap<String,Object>();body.put("schoolEmail",email);body.put("emailVerificationToken",token);
        body.put("password",password);body.put("passwordConfirm",password);body.put("nickname",nickname());
        body.put("gender","남자");body.put("dormitory","UM dormitory");body.put("faculty","UM faculty A");body.put("major","UM major A");
        body.put("birthYear",2002);body.put("admissionYear","24");body.put("grade","3학년");
        body.put("mbtiEnergy","I");body.put("mbtiInformation","N");body.put("mbtiDecision","T");body.put("mbtiLifestyle","J");return body;
    }
    static void noMember(String email,Map<String,Object> body)throws Exception{
        check(query("SELECT COUNT(*) FROM user WHERE school_email=?",email)==0,"Rejected signup created user");
        check(query("SELECT COUNT(*) FROM user_profile WHERE nickname=?",body.get("nickname"))==0,"Rejected signup created profile");
    }
    static void error(Reply r,String code){expect(r,400,code);check(!r.json().path("success").asBoolean()&&r.json().path("data").isNull(),"Failure envelope/data changed");}
    static void oldPassword(Member member,Object snapshot)throws Exception{
        check(snapshot.equals(accountSnapshot(member)),"Rejected password operation changed account/token rows");
        try(var c=UserMultiSupport.open()){
            String hash=UserMultiSupport.rows(c,"SELECT password FROM user WHERE user_id=?",member.id()).getFirst().get("password").toString();
            check(encoder.matches(PASSWORD,hash),"Old password no longer matches");
        }
        expect(login(PORT,member.email(),PASSWORD,ip()),200,"");
    }
    static String resetToken(Member member)throws Exception{
        expect(auth("password-reset/request",Map.of("email",member.email())),200,"");return token(member.email());
    }
    static Map<String,Object> resetBody(String token,String password){return new LinkedHashMap<>(Map.of("resetToken",token,"newPassword",password,"newPasswordConfirm",password));}
    static String boundary(boolean multibyte,int bytes){
        String base=multibyte?"Aa1!"+"가".repeat(22):"Aa1!"+"x".repeat(66);
        String password=base+"x".repeat(bytes-base.getBytes(StandardCharsets.UTF_8).length);
        check(password.getBytes(StandardCharsets.UTF_8).length==bytes,"Invalid boundary fixture");return password;
    }
    static Map<String,Object> availability(String variant)throws Exception{
        Member member=member(false);String usedNickname="한글검증";
        exec("UPDATE user_profile SET nickname=? WHERE user_id=?",usedNickname,member.id());
        String freeEmail=email("available"),freeNickname="가용닉네임";
        String email=null,nick=null;boolean emailAvailable=true,nicknameAvailable=true;
        switch(variant){
            case "no-params" -> {}
            case "empty" -> {email="";nick="";}
            case "email-only" -> {email=member.email();emailAvailable=false;}
            case "korean-only" -> {nick=usedNickname;nicknameAvailable=false;}
            case "both-free" -> {email=freeEmail;nick=freeNickname;}
            case "both-used" -> {email=member.email();nick=usedNickname;emailAvailable=false;nicknameAvailable=false;}
            case "mixed" -> {email=freeEmail;nick=usedNickname;nicknameAvailable=false;}
            case "format-unvalidated" -> {email="not-an-email";nick="!";}
            default -> throw new AssertionError("Unknown availability fixture");
        }
        String path="/api/auth/signup/availability";var parts=new ArrayList<String>();
        if(email!=null)parts.add("schoolEmail="+URLEncoder.encode(email,StandardCharsets.UTF_8));
        if(nick!=null)parts.add("nickname="+URLEncoder.encode(nick,StandardCharsets.UTF_8));
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+PORT+path+(parts.isEmpty()?"":"?"+String.join("&",parts))))
            .timeout(Duration.ofSeconds(20)).header("X-User-Validation","UQ"+auditSequence.incrementAndGet()).GET().build();
        long users=query("SELECT COUNT(*) FROM user"),profiles=query("SELECT COUNT(*) FROM user_profile");
        var r=send(PORT,path,request);expect(r,200,"");var data=r.json().path("data");
        check(data.size()==2&&data.path("schoolEmailAvailable").isBoolean()&&data.path("nicknameAvailable").isBoolean(),"Availability response fields/types changed");
        check(data.path("schoolEmailAvailable").asBoolean()==emailAvailable&&data.path("nicknameAvailable").asBoolean()==nicknameAvailable,"Availability values differ");
        check(users==query("SELECT COUNT(*) FROM user")&&profiles==query("SELECT COUNT(*) FROM user_profile"),"Availability created rows");
        // Reuse this synthetic nickname in the next isolated case.
        exec("UPDATE user_profile SET nickname=? WHERE user_id=?",nickname(),member.id());return ok();
    }
    static Map<String,Object> expiredCode()throws Exception{
        String email=email("expiredcode");expect(auth("email-verifications/send",Map.of("email",email,"purpose","SIGNUP")),200,"");
        expire("email_verification","expires_at","email",email);
        Object before=verificationSnapshot(email);
        var r=auth("email-verifications/verify",Map.of("email",email,"purpose","SIGNUP","code",code(email)));
        error(r,"EMAIL_VERIFICATION_EXPIRED");check(!r.json().has("verificationToken"),"Token present on error");
        check(before.equals(verificationSnapshot(email)),"Expired code issued/persisted token");
        check(query("SELECT COUNT(*) FROM email_verification WHERE email=? AND (verified_at IS NOT NULL OR verification_token_hash IS NOT NULL OR consumed_at IS NOT NULL)",email)==0,"Expired code changed verification state");return ok();
    }
    static Map<String,Object> rejectedSignup(boolean expired)throws Exception{
        String source=email("signupsource"),token=signupToken(source),target=expired?source:email("different");
        if(expired)expire("email_verification","verification_token_expires_at","email",source);
        Object before=verificationSnapshot(source);var body=signupBody(target,token,PASSWORD);
        error(auth("signup",body),"EMAIL_VERIFICATION_TOKEN_INVALID");noMember(target,body);
        check(before.equals(verificationSnapshot(source)),"Rejected signup consumed token");return ok();
    }
    static Map<String,Object> rejectedReset(boolean expired)throws Exception{
        Member member=member(true);String token=expired?resetToken(member):randomSecret();
        if(expired)expire("password_reset_token","expires_at","user_id",member.id());
        Object before=accountSnapshot(member);
        error(auth("password-reset/confirm",resetBody(token,NEW_PASSWORD)),expired?"PASSWORD_RESET_TOKEN_EXPIRED":"PASSWORD_RESET_TOKEN_INVALID");
        oldPassword(member,before);return ok();
    }
    static Map<String,Object> passwordBoundary(String route,boolean multibyte,int bytes)throws Exception{
        String password=boundary(multibyte,bytes);boolean accepted=bytes==72;
        if(route.equals("signup")){
            String email=email("byteSignup"),token=signupToken(email);Object before=verificationSnapshot(email);
            var body=signupBody(email,token,password);var r=auth("signup",body);
            if(accepted){expect(r,200,"");check(query("SELECT COUNT(*) FROM user WHERE school_email=?",email)==1,"Boundary signup absent");expect(login(PORT,email,password,ip()),200,"");}
            else{error(r,"VALIDATION_ERROR");noMember(email,body);check(before.equals(verificationSnapshot(email)),"Invalid password consumed signup token");}
        }else{
            Member member=member(true);
            if(route.equals("login")){
                if(accepted)exec("UPDATE user SET password=? WHERE user_id=?",encoder.encode(password),member.id());
                Object before=accountSnapshot(member);var r=login(PORT,member.email(),password,ip());
                if(accepted)expect(r,200,"");else{error(r,"VALIDATION_ERROR");oldPassword(member,before);}
            }else{
                String token=route.equals("reset")?resetToken(member):"";Object before=accountSnapshot(member);
                var r=route.equals("reset")?auth("password-reset/confirm",resetBody(token,password)):
                    call("PATCH","/api/users/me/password",Map.of("currentPassword",PASSWORD,"newPassword",password,"newPasswordConfirm",password),member.jwt());
                if(accepted){
                    expect(r,200,"");check(r.json().path("data").isNull(),"Password success data must be null");expect(login(PORT,member.email(),password,ip()),200,"");
                    if(route.equals("change")){
                        // The same boundary value must also work as currentPassword, without truncation.
                        expect(call("PATCH","/api/users/me/password",Map.of("currentPassword",password,"newPassword",NEW_PASSWORD,"newPasswordConfirm",NEW_PASSWORD),member.jwt()),200,"");
                        expect(login(PORT,member.email(),NEW_PASSWORD,ip()),200,"");
                    }
                }
                else{error(r,"VALIDATION_ERROR");oldPassword(member,before);}
            }
        }
        return Map.of("utf8Bytes",bytes,"multibyte",multibyte,"accepted",accepted);
    }
    static Map<String,Object> passwordRejection(String route,String variant)throws Exception{
        if(route.equals("signup")){
            String email=email("confirmation"),token=signupToken(email);Object before=verificationSnapshot(email);
            var body=signupBody(email,token,PASSWORD);body.put("passwordConfirm",variant.equals("confirm-overlong")?boundary(true,73):NEW_PASSWORD);
            error(auth("signup",body),"VALIDATION_ERROR");noMember(email,body);check(before.equals(verificationSnapshot(email)),"Invalid confirmation consumed token");
        }else{
            Member member=member(true);String token=route.equals("reset")?resetToken(member):"";Object before=accountSnapshot(member);
            Map<String,Object> body=route.equals("reset")?resetBody(token,NEW_PASSWORD):new LinkedHashMap<>(Map.of("currentPassword",PASSWORD,"newPassword",NEW_PASSWORD,"newPasswordConfirm",NEW_PASSWORD));
            switch(variant){
                case "mismatch" -> body.put("newPasswordConfirm",PASSWORD);
                case "confirm-overlong" -> body.put("newPasswordConfirm",boundary(true,73));
                case "current-wrong" -> body.put("currentPassword","WrongCurrent1!");
                case "current-overlong" -> body.put("currentPassword",boundary(true,73));
                default -> throw new AssertionError("Unknown password rejection fixture");
            }
            var r=route.equals("reset")?auth("password-reset/confirm",body):call("PATCH","/api/users/me/password",body,member.jwt());
            error(r,variant.equals("current-wrong")?"INVALID_REQUEST":"VALIDATION_ERROR");oldPassword(member,before);
        }
        return ok();
    }
    static Map<String,Object> weightBoundary(String variant)throws Exception{
        Member member=member(true);var body=surveyBody(true);
        // Establish a valid non-boundary baseline as fixture setup only.
        body.put("weights",weights(3));expect(call("PUT",PREFERENCE,body,member.jwt()),200,"");idle();Object before=surveySnapshot(member);
        Map<String,Integer> w=weights(3);
        switch(variant){
            case "one" -> w=weights(1);
            case "zero" -> w.put("meal",0);
            case "six" -> w.put("meal",6);
            case "null-value" -> w.put("meal",null);
            case "missing-key" -> w.remove("meal");
            case "extra-key" -> w.put("unsupported",3);
            case "empty" -> w.clear();
            case "null-map" -> w=null;
            case "missing-map" -> {}
            default -> throw new AssertionError("Unknown weight fixture");
        }
        if(variant.equals("missing-map"))body.remove("weights");else body.put("weights",w);
        var r=call("PUT",PREFERENCE,body,member.jwt());
        if(variant.equals("one")){
            expect(r,200,"");var get=call("GET",PREFERENCE,null,member.jwt());expect(get,200,"");
            check(JSON.mapper.valueToTree(w).equals(get.json().path("data").path("answers").path("weights")),"Weight lower bound roundtrip differs");
            check(query("SELECT COUNT(*) FROM preference_criterion w JOIN roommate_preference p ON p.preference_id=w.preference_id WHERE p.user_id=? AND weight=1",member.id())==18,"Lower bound not fully persisted");
        }else{error(r,"VALIDATION_ERROR");idle();check(before.equals(surveySnapshot(member)),"Invalid weight changed persisted survey");}
        return ok();
    }
    static Map<String,Object> optionBoundary(String variant)throws Exception{
        Member member=member(true);var body=surveyBody(true);body.put("weights",weights(3));
        expect(call("PUT",PREFERENCE,body,member.jwt()),200,"");idle();Object before=surveySnapshot(member);
        switch(variant){
            case "empty" -> body.put("age",List.of());
            case "null-list" -> body.put("age",null);
            case "null-element" -> body.put("age",Arrays.asList((String)null));
            case "blank" -> body.put("age",List.of(" "));
            case "duplicate" -> body.put("age",List.of("동갑","동갑"));
            case "unknown" -> body.put("age",List.of("지원하지않는값"));
            case "too-long" -> body.put("age",List.of("가".repeat(61)));
            case "scalar-unknown" -> body.put("department","지원하지않는값");
            case "scalar-too-long" -> body.put("department","가".repeat(61));
            case "no-preference-alone" -> body.put("age",List.of("상관없음"));
            default -> throw new AssertionError("Unknown option fixture");
        }
        var r=call("PUT",PREFERENCE,body,member.jwt());
        if(variant.equals("no-preference-alone")){
            expect(r,200,"");var get=call("GET",PREFERENCE,null,member.jwt());expect(get,200,"");
            check(JSON.mapper.valueToTree(List.of("상관없음")).equals(get.json().path("data").path("answers").path("age")),"Single no-preference option differs");
        }else{error(r,"VALIDATION_ERROR");idle();check(before.equals(surveySnapshot(member)),"Invalid option changed persisted survey");}
        return ok();
    }
    static Map<String,Object> incompleteProfile(String variant)throws Exception{
        Member member=member(true);var body=surveyBody(false);expect(call("PUT",PROFILE,body,member.jwt()),200,"");idle();
        switch(variant){
            case "flag-false" -> exec("UPDATE profile_survey SET completed=false WHERE user_id=?",member.id());
            case "invalid-scalar" -> exec("UPDATE profile_survey SET meal='legacy-invalid' WHERE user_id=?",member.id());
            case "missing-options" -> exec("DELETE o FROM profile_survey_option o JOIN profile_survey p ON p.profile_survey_id=o.profile_survey_id WHERE p.user_id=?",member.id());
            default -> throw new AssertionError("Unknown incomplete profile fixture");
        }
        Object before=surveySnapshot(member);var expected=new LinkedHashMap<>(body);String intro=(String)expected.remove("introduction");
        if(variant.equals("invalid-scalar"))expected.put("meal","legacy-invalid");
        if(variant.equals("missing-options")){expected.put("dormStayDays",List.of());expected.put("mealPlan",List.of());}
        for(int repeat=0;repeat<2;repeat++){
            var r=call("GET",PROFILE,null,member.jwt());expect(r,200,"");var data=r.json().path("data");
            check(!data.path("completed").asBoolean(true),"Incomplete stored profile reported complete");
            check(JSON.mapper.valueToTree(expected).equals(data.path("answers")),"Incomplete answers were discarded/rewritten");
            check(data.path("introduction").asString().equals(intro),"Incomplete profile lost introduction");
            check(before.equals(surveySnapshot(member)),"GET rewrote stored incomplete survey");
        }
        return ok();
    }
    static void plan(){
        for(String variant:List.of("no-params","empty","email-only","korean-only","both-free","both-used","mixed","format-unvalidated"))
            add("availability-"+variant,"P0","200 boolean availability fields; no rows created",()->availability(variant));
        add("verification-expired-code-no-token","P0","400 EMAIL_VERIFICATION_EXPIRED; no verification token issued",AusHttp5::expiredCode);
        add("signup-expired-token-no-member","P0","400 EMAIL_VERIFICATION_TOKEN_INVALID; no user/profile; token unchanged",()->rejectedSignup(true));
        add("signup-other-email-token-no-member","P0","400 EMAIL_VERIFICATION_TOKEN_INVALID; no user/profile; source token unchanged",()->rejectedSignup(false));
        add("reset-unregistered-token-keeps-password","P0","400 PASSWORD_RESET_TOKEN_INVALID; password unchanged",()->rejectedReset(false));
        add("reset-expired-token-keeps-password","P0","400 PASSWORD_RESET_TOKEN_EXPIRED; password/token unchanged",()->rejectedReset(true));
        for(String route:List.of("signup","login","change","reset"))for(boolean multi:List.of(false,true))for(int bytes:List.of(72,73))
            add("password-"+route+"-"+(multi?"utf8":"ascii")+"-"+bytes,"P1",bytes==72?"200; full password accepted":"400 VALIDATION_ERROR; no account/token mutation",()->passwordBoundary(route,multi,bytes));
        for(String route:List.of("signup","change","reset"))for(String variant:List.of("mismatch","confirm-overlong"))
            add("password-"+route+"-"+variant,"P1","400 VALIDATION_ERROR; no account/token mutation",()->passwordRejection(route,variant));
        for(String variant:List.of("current-wrong","current-overlong"))add("password-change-"+variant,"P1",variant.equals("current-wrong")?"400 INVALID_REQUEST; password unchanged":"400 VALIDATION_ERROR; password unchanged",()->passwordRejection("change",variant));
        for(String variant:List.of("one","zero","six","null-value","missing-key","extra-key","empty","null-map","missing-map"))
            add("preference-weight-"+variant,"P1",variant.equals("one")?"200; all 18 weights saved as 1":"400 VALIDATION_ERROR; prior survey preserved",()->weightBoundary(variant));
        for(String variant:List.of("empty","null-list","null-element","blank","duplicate","unknown","too-long","scalar-unknown","scalar-too-long","no-preference-alone"))
            add("preference-option-"+variant,"P1",variant.equals("no-preference-alone")?"200; option roundtrip":"400 VALIDATION_ERROR; prior survey preserved",()->optionBoundary(variant));
        for(String variant:List.of("flag-false","invalid-scalar","missing-options"))
            add("profile-incomplete-"+variant,"P1","200 completed=false; answers/introduction retained; repeated GET never writes",()->incompleteProfile(variant));
    }
    static boolean runPhase(String priority)throws Exception{
        for(var scenario:PLAN)if(scenario.priority().equals(priority)){
            if(CASES.stream().anyMatch(c->c.get("name").equals(scenario.name())&&"PASS".equals(c.get("status"))))continue;
            test(scenario.name(),scenario.action());
            var record=CASES.getLast();record.put("priority",priority);record.put("expected",scenario.expected());
            if(scenario.name().equals("verification-expired-code-no-token")||scenario.name().equals("signup-expired-token-no-member")||scenario.name().equals("reset-expired-token-keeps-password"))
                record.put("expirationMethod","fixture timestamp moved into past; no real TTL wait");
            UserMultiSupport.write(out.resolve("cases.json"),CASES);
            UserMultiSupport.write(out.resolve("requests.json"),REQUESTS);
            if(!record.get("status").equals("PASS"))return false;
        }
        return true;
    }
    static void awaitP0Review(Path previous)throws Exception{
        idle();Path checkpoint=out.resolve("p0-checkpoint.json"),review=out.resolve("p0-reviewed.json");
        var p0=CASES.stream().filter(c->"P0".equals(c.get("priority"))).toList();
        check(p0.size()==13&&p0.stream().allMatch(c->"PASS".equals(c.get("status"))),"P0 checkpoint requires all 13 PASS");
        UserMultiSupport.write(checkpoint,Map.of("run",RUN,"p0Passed",13,"p0Failed",0,"httpRequestsIncludingSetup",REQUESTS.size(),"cases",p0,"p1Started",false));
        if(previous!=null&&previous.getFileName().toString().equals("2026-10-22-a0115b20ea32")){
            var reviewed=JSON.mapper.readTree(Files.readAllBytes(previous.resolve("p0-reviewed.json")));
            check(reviewed.path("run").asString().equals("a0115b20ea32")&&reviewed.path("continueP1").asBoolean(),"Prior P0 review is not valid");
            UserMultiSupport.write(review,Map.of("run",RUN,"continueP1",true,"priorReview",previous.getFileName().toString(),"reviewedAt",Instant.now().toString()));
        }
        System.out.println("P0_REVIEW_REQUIRED checkpoint="+checkpoint);System.out.flush();
        long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.MINUTES.toNanos(10);
        while(!Files.exists(review)){
            check(System.nanoTime()<deadline,"P0 review timeout; P1 remains NOT RUN");Thread.sleep(250);
        }
        var decision=JSON.mapper.readTree(Files.readAllBytes(review));
        check(decision.path("run").asString().equals(RUN)&&decision.path("continueP1").asBoolean(),"P0 review did not authorize P1 continuation");
        System.out.println("P0_REVIEW_CONFIRMED; starting P1");System.out.flush();
    }
    public static void main(String[] args)throws Exception{
        plan();
        if(args.length==1&&args[0].equals("--list")){
            System.out.println(JSON.mapper.writeValueAsString(PLAN.stream().map(s->Map.of("name",s.name(),"priority",s.priority(),"expected",s.expected(),"status","NOT RUN")).toList()));return;
        }
        check(args.length==2||args.length==4&&args[2].equals("--continue"),"Expected repository root and prepared JAR SHA");
        root=Path.of(args[0]).toRealPath();Path work=root.resolve("backend/build/auth-user-survey-http5");
        Path previous=args.length==4?Path.of(args[3]).toRealPath():null;
        if(previous!=null)check(previous.getParent().equals(work.resolve("runs").toRealPath())&&Set.of("2026-10-22-82bee9f0fc29","2026-10-22-32da878213d5","2026-10-22-a0115b20ea32").contains(previous.getFileName().toString()),"Only a diagnosed harness-error run can be continued");
        // Neither marker is removed/overwritten. Continuation never reapplies DDL or repeats passed cases.
        Files.writeString(work.resolve(previous==null?"execution-started.json":"continue-from-"+previous.getFileName()+".json"),"{\"started\":\""+Instant.now()+"\"}\n",StandardOpenOption.CREATE_NEW);
        out=work.resolve("runs/"+LocalDate.now()+"-"+RUN);Files.createDirectories(out);
        UserMultiSupport.database=System.getenv("KKIRI_MI_DATABASE");UserMultiSupport.account=System.getenv("KKIRI_MI_USER");
        UserMultiSupport.password=System.getenv("KKIRI_MI_PASSWORD");jwt=randomSecret();ipc=randomSecret();
        ((ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).setLevel(ch.qos.logback.classic.Level.OFF);
        boolean completed=false,cleaned=false;var before=sourceHashes();
        int priorRequests=0;
        try{
            portFree(PORT);
            try(var c=UserMultiSupport.open()){
                UserMultiSupport.grants(c);
                long count=UserMultiSupport.number(c,"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()");
                if(previous==null)check(count==0,"New schema must have no tables");
                else{
                    var priorSummary=JSON.mapper.readTree(Files.readString(previous.resolve("summary.json")));
                    check(priorSummary.path("allProcessesStopped").asBoolean()&&priorSummary.path("allTablesEmpty").asBoolean()&&priorSummary.path("sourcesAndPriorEvidenceUnchanged").asBoolean()&&priorSummary.path("bootJarSha256").asString().equals(args[1]),"Prior run must be stopped, empty and unchanged");
                    check(count==31,"Continuation requires the retained 31-table schema");
                    tables=UserMultiSupport.rows(c,"SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() ORDER BY table_name").stream().map(r->r.values().iterator().next().toString()).toList();
                    for(String table:tables){check(table.matches("[a-z_]+"),"Unsafe continuation table");check(UserMultiSupport.number(c,"SELECT COUNT(*) FROM `"+table+"`")==0,"Continuation requires all fixture rows already empty");}
                    initialSchema=schema(c);
                    String priorDdl=JSON.mapper.writeValueAsString(JSON.mapper.readValue(Files.readString(previous.resolve("initial-schema.json")),Map.class));
                    check(priorDdl.replaceAll(" AUTO_INCREMENT=\\d+","").equals(JSON.mapper.writeValueAsString(initialSchema).replaceAll(" AUTO_INCREMENT=\\d+","")),"Retained schema changed after initial run");
                    List<Map<String,Object>> priorCases=JSON.mapper.readValue(Files.readString(previous.resolve("cases.json")),List.class);
                    boolean reviewedP0=previous.getFileName().toString().equals("2026-10-22-a0115b20ea32");
                    check(reviewedP0?priorCases.size()==14&&priorCases.getLast().get("name").equals("harness")&&priorCases.getLast().get("status").equals("INCOMPLETE"):
                        priorCases.size()==9&&priorCases.getLast().get("name").equals("verification-expired-code-no-token")&&priorCases.getLast().get("status").equals("FAIL"),"Unexpected prior failure scope");
                    int carried=reviewedP0?13:8;
                    for(var record:priorCases.subList(0,carried)){
                        check(record.get("status").equals("PASS")&&PLAN.stream().anyMatch(p->p.priority().equals("P0")&&p.name().equals(record.get("name"))),"Only prior approved P0 PASS cases may carry forward");
                        var copy=new LinkedHashMap<>(record);copy.put("carriedForward",true);copy.putIfAbsent("evidenceRun",previous.getFileName().toString());CASES.add(copy);
                    }
                    priorRequests=priorSummary.path("totalHttpRequestsIncludingPriorAttempt").asInt(priorSummary.path("httpRequestsIncludingFixtureSetup").asInt());
                    UserMultiSupport.write(out.resolve("continuation.json"),Map.of("previousRun",previous.getFileName().toString(),"passedCasesCarriedForward",carried,"previousHttpRequests",priorRequests,"priorHarnessEvidencePreserved",true,"schemaRecreated",false,"reason",reviewedP0?"P0 passed and was reviewed; byte-based JSON parsing accepts PowerShell UTF-8 BOM; continue P1 only":"Corrected expiration fixture binding and verified effective expiry"));
                    UserMultiSupport.write(out.resolve("initial-schema.json"),initialSchema);
                }
                UserMultiSupport.write(out.resolve("environment.json"),Map.of("identity",UserMultiSupport.rows(c,"SELECT DATABASE() db,CURRENT_USER() account,VERSION() version,@@server_uuid uuid,@@hostname hostname,@@session.time_zone zone"),"grants",UserMultiSupport.rows(c,"SHOW GRANTS"),"httpPort",PORT,"realSmtp",false));
            }
            if(previous==null){generateSchema();check(entityCount==31,"Mapped entity count changed; review the plan");applySchema();}
            startMailCapture();a=startServer(PORT);b=a;basics();
            if(runPhase("P0")){
                awaitP0Review(previous);
                completed=runPhase("P1");
            }
        }catch(Throwable failure){CASES.add(Map.of("name","harness","status","INCOMPLETE","error",UserMultiSupport.error(failure)));}
        finally{
            for(var server:SERVERS)try{server.stop();}catch(Throwable failure){CASES.add(Map.of("name","server-stop","status","INCOMPLETE","error",UserMultiSupport.error(failure)));}
            if(mailSocket!=null)mailSocket.close();mailExecutor.shutdownNow();
            boolean stopped=SERVERS.stream().noneMatch(s->s.process.isAlive());
            if(tables!=null&&stopped)try{cleanup();cleaned=true;}catch(Throwable failure){CASES.add(Map.of("name","cleanup","status","INCOMPLETE","error",UserMultiSupport.error(failure)));}
            var after=sourceHashes();boolean unchanged=before.equals(after);
            UserMultiSupport.write(out.resolve("source-hashes.json"),after);
            UserMultiSupport.write(out.resolve("requests.json"),REQUESTS);UserMultiSupport.write(out.resolve("servers.json"),EVENTS);UserMultiSupport.write(out.resolve("cases.json"),CASES);
            var outcomes=new ArrayList<Map<String,Object>>();
            for(var scenario:PLAN){
                var outcome=new LinkedHashMap<String,Object>();outcome.put("name",scenario.name());outcome.put("priority",scenario.priority());outcome.put("expected",scenario.expected());
                var actual=CASES.stream().filter(c->c.get("name").equals(scenario.name())).findFirst();
                if(actual.isPresent())outcome.putAll(actual.get());else outcome.put("status","NOT RUN");outcomes.add(outcome);
            }
            UserMultiSupport.write(out.resolve("outcomes.json"),outcomes);
            var summary=new LinkedHashMap<String,Object>();
            String verdict=!completed||!cleaned||!stopped||!unchanged||CASES.stream().anyMatch(c->c.get("status").equals("INCOMPLETE"))?"INCOMPLETE":CASES.stream().anyMatch(c->c.get("status").equals("FAIL"))?"FAIL":"PASS";
            summary.put("verdict",verdict);summary.put("planned",PLAN.size());summary.put("passed",CASES.stream().filter(c->c.get("status").equals("PASS")).count());summary.put("failed",CASES.stream().filter(c->c.get("status").equals("FAIL")).count());
            summary.put("httpRequestsIncludingFixtureSetup",REQUESTS.size());summary.put("allProcessesStopped",stopped);summary.put("allTablesEmpty",cleaned);summary.put("sourcesAndPriorEvidenceUnchanged",unchanged);summary.put("bootJarSha256",args[1]);
            summary.put("priorHttpRequests",priorRequests);summary.put("totalHttpRequestsIncludingPriorAttempt",priorRequests+REQUESTS.size());summary.put("casesExecutedThisRun",CASES.stream().filter(c->!Boolean.TRUE.equals(c.get("carriedForward"))&&PLAN.stream().anyMatch(p->p.name().equals(c.get("name")))).count());
            summary.put("notRun",outcomes.stream().filter(c->c.get("status").equals("NOT RUN")).count());
            var phases=new LinkedHashMap<String,Object>();
            for(String priority:List.of("P0","P1")){
                var counts=new LinkedHashMap<String,Long>();
                for(String status:List.of("PASS","FAIL","BLOCKED","NOT RUN"))counts.put(status,outcomes.stream().filter(c->c.get("priority").equals(priority)&&c.get("status").equals(status)).count());
                phases.put(priority,counts);
            }
            summary.put("phases",phases);summary.put("realTtlElapsedValidation","NOT RUN; only fixture timestamps were adjusted in three approved expiration cases");
            summary.put("retainedResources",List.of("kkiri-aus-http5-mysql-20261022","kkiri-aus-http5-data-20261022","kkiriaus5http20261022","aus5http20261022"));
            summary.put("blocked",List.of("real SMTP","HTTPS","ingress"));UserMultiSupport.write(out.resolve("summary.json"),summary);
            System.out.println("RESULT "+verdict+" evidence="+out);if(!verdict.equals("PASS"))System.exit(1);
        }
    }
}
