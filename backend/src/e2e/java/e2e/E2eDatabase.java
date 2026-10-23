package e2e;

import java.sql.*;
import java.util.*;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import com.kkirikkiri.user.entity.*;

/** Destructive fixtures are restricted to a newly owned Docker instance and schema. */
public final class E2eDatabase {
    static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing E2E setting: " + name);
        return value;
    }
    static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
    static String database() {
        String name = required("E2E_DATABASE");
        check(name.matches("kkiri_e2e_[0-9a-f]{12}"), "Unsafe E2E schema");
        return name;
    }
    static String account() {
        String name = required("E2E_DB_USER");
        check(name.matches("e2e_[0-9a-f]{12}") && database().endsWith(name.substring(4)), "Unsafe E2E account");
        return name;
    }
    static String url() {
        return "jdbc:mysql://127.0.0.1:13316/" + database()
            + "?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&allowPublicKeyRetrieval=true"
            + "&useSSL=false&autoReconnect=false&allowMultiQueries=false&allowLoadLocalInfile=false&connectTimeout=5000&socketTimeout=15000";
    }
    static DriverManagerDataSource datasource() {
        check("1".equals(required("E2E_MANAGED")), "Use the managed E2E runner");
        return new DriverManagerDataSource(url(), account(), required("E2E_DB_PASSWORD"));
    }
    static Connection guardedConnection() throws Exception {
        Connection c = datasource().getConnection();
        try {
            try (var s = c.createStatement(); var r = s.executeQuery(
                    "SELECT DATABASE(),CURRENT_USER(),VERSION(),@@server_uuid,@@hostname,@@port,@@session.time_zone")) {
                check(r.next() && database().equals(r.getString(1)) && (account() + "@%").equals(r.getString(2))
                    && r.getString(3).startsWith("8.0.") && required("E2E_DB_UUID").equals(r.getString(4))
                    && "kkiri-e2e".equals(r.getString(5)) && r.getInt(6) == 3306
                    && "+00:00".equals(r.getString(7)), "E2E database identity mismatch");
            }
            Set<String> grants = new HashSet<>();
            try (var s = c.createStatement(); var r = s.executeQuery("SHOW GRANTS")) {
                while (r.next()) grants.add(r.getString(1));
            }
            check(grants.equals(Set.of("GRANT USAGE ON *.* TO `" + account() + "`@`%`",
                "GRANT ALL PRIVILEGES ON `" + database() + "`.* TO `" + account() + "`@`%`"))
                || grants.equals(Set.of("GRANT USAGE ON *.* TO `" + account() + "`@`%`",
                "GRANT ALL PRIVILEGES ON `" + database().replace("_", "\\_") + "`.* TO `" + account() + "`@`%`")),
                "Unexpected E2E privileges");
            return c;
        } catch (Throwable failure) { c.close(); throw failure; }
    }
    static List<String> tables(Connection c) throws Exception {
        var names = new ArrayList<String>();
        try (var s = c.prepareStatement("SELECT table_name FROM information_schema.tables WHERE table_schema=? AND table_type='BASE TABLE'")) {
            s.setString(1, database());
            try (var r = s.executeQuery()) { while (r.next()) names.add(r.getString(1)); }
        }
        return names;
    }
    static LocalContainerEntityManagerFactoryBean factory(String ddl) {
        var factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(datasource());
        factory.setPackagesToScan("com.kkirikkiri");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", ddl,
            "hibernate.hbm2ddl.halt_on_error", "true", "hibernate.show_sql", "false",
            "hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl"));
        factory.afterPropertiesSet();
        return factory;
    }
    static void clean() throws Exception {
        try (var c = guardedConnection(); var s = c.createStatement()) {
            var names = tables(c);
            s.execute("SET FOREIGN_KEY_CHECKS=0");
            try {
                for (String name : names) {
                    check(name.matches("[a-z_]+"), "Unexpected E2E table name");
                    s.executeUpdate("DELETE FROM `" + name + "`");
                }
            } finally { s.execute("SET FOREIGN_KEY_CHECKS=1"); }
            for (String name : names) {
                try (var r = s.executeQuery("SELECT COUNT(*) FROM `" + name + "`")) {
                    check(r.next() && r.getLong(1) == 0, "Residual E2E data");
                }
            }
        }
    }
    public static void main(String[] args) throws Exception {
        String mode = args.length == 1 ? args[0] : "";
        check(Set.of("prepare", "reset", "clean", "verify", "snapshot").contains(mode), "Unknown E2E fixture operation");
        try (var c = guardedConnection()) {
            if (mode.equals("prepare")) check(tables(c).isEmpty(), "Refusing to initialize an existing schema");
        }
        if (mode.equals("prepare")) { var f = factory("create-only"); f.destroy(); }
        if (mode.equals("clean") || mode.equals("reset")) clean();
        if (mode.equals("reset")) {
            var f = factory("validate");
            try (var em = f.getObject().createEntityManager()) {
                var tx = em.getTransaction(); tx.begin();
                try {
                    var dorm = new Dormitory("E2E", "E2E생활관", true);
                    var faculty = new Faculty("E2E학부", true);
                    var user = new User(new BCryptPasswordEncoder().encode(required("E2E_LOGIN_PASSWORD")), required("E2E_LOGIN_EMAIL"));
                    user.verifyEmail();
                    em.persist(dorm); em.persist(faculty); em.persist(user);
                    em.persist(new Dormitory("NARAE", "나래", true));
                    em.persist(new Faculty("HK자율전공학부", true));
                    var computer = new Faculty("컴퓨터응용수학부", true);
                    var law = new Faculty("법경영학부", true);
                    em.persist(computer); em.persist(law);
                    em.persist(new Major(computer, "소프트웨어융합전공", true));
                    em.persist(new Major(computer, "정보보안전공", true));
                    em.persist(new Major(law, "법학전공", true));
                    em.persist(new UserProfile(user, "E2E사용자", "남자", dorm, faculty, null, (short)2000, "20", "1학년", "INTJ"));
                    var peer = new User(new BCryptPasswordEncoder().encode(required("E2E_LOGIN_PASSWORD")), required("E2E_SECOND_EMAIL"));
                    var admin = new User(new BCryptPasswordEncoder().encode(required("E2E_LOGIN_PASSWORD")), required("E2E_ADMIN_EMAIL"), UserRole.ADMIN);
                    peer.verifyEmail(); admin.verifyEmail(); em.persist(peer); em.persist(admin);
                    em.persist(new UserProfile(peer, "E2E상대", "남자", dorm, faculty, null, (short)2000, "20", "1학년", "INTJ"));
                    em.persist(new UserProfile(admin, "E2E관리자", "남자", dorm, faculty, null, (short)2000, "20", "1학년", "INTJ"));
                    if ("1".equals(System.getenv("E2E_AUS_P1"))) {
                        for (var status : List.of(UserStatus.SUSPENDED, UserStatus.WITHDRAWN)) {
                            var inactive = new User(new BCryptPasswordEncoder().encode(required("E2E_LOGIN_PASSWORD")),
                                required(status == UserStatus.SUSPENDED ? "E2E_SUSPENDED_EMAIL" : "E2E_WITHDRAWN_EMAIL"));
                            inactive.verifyEmail(); inactive.changeStatus(status); em.persist(inactive);
                        }
                    }
                    tx.commit();
                } catch (Throwable failure) { if (tx.isActive()) tx.rollback(); throw failure; }
            } finally { f.destroy(); }
        }
        if (mode.equals("verify")) {
            try (var c = guardedConnection(); var s = c.prepareStatement(
                    "SELECT COUNT(*) FROM user u JOIN user_profile p ON u.user_id=p.user_id WHERE u.school_email=? AND u.status='ACTIVE'")) {
                s.setString(1, required("E2E_LOGIN_EMAIL"));
                try (var r = s.executeQuery()) { check(r.next() && r.getInt(1) == 1, "E2E login account missing from MySQL"); }
            }
        }
        if (mode.equals("snapshot")) {
            var snapshot = new LinkedHashMap<String,Object>();
            // Fixed, read-only business-data projection; never selects credentials or verification codes.
            Map<String,String> queries = new LinkedHashMap<>();
            queries.put("profiles", "SELECT p.user_id,p.nickname,p.introduction,p.grade,p.mbti,p.major_id,f.faculty_name,m.major_name,d.dormitory_name dormitory FROM user_profile p JOIN faculty f ON f.faculty_id=p.faculty_id LEFT JOIN major m ON m.major_id=p.major_id JOIN dormitory d ON d.dormitory_id=p.dormitory_id ORDER BY p.user_id");
            queries.put("surveys", "SELECT * FROM profile_survey ORDER BY user_id");
            queries.put("surveyOptions", "SELECT o.*,s.user_id FROM profile_survey_option o JOIN profile_survey s ON s.profile_survey_id=o.profile_survey_id ORDER BY s.user_id,o.field_code,o.option_value");
            queries.put("preferences", "SELECT * FROM roommate_preference ORDER BY user_id");
            queries.put("preferenceOptions", "SELECT o.*,p.user_id FROM roommate_preference_option o JOIN roommate_preference p ON p.preference_id=o.preference_id ORDER BY p.user_id,o.criterion_code,o.option_value");
            queries.put("criteria", "SELECT c.*,p.user_id FROM preference_criterion c JOIN roommate_preference p ON p.preference_id=c.preference_id ORDER BY p.user_id,c.criterion_code");
            queries.put("resetStates", "SELECT user_id,used_at IS NOT NULL used FROM password_reset_token ORDER BY password_reset_token_id");
            queries.put("matches", "SELECT requester_user_id,target_user_id,match_score FROM roommate_match ORDER BY requester_user_id,target_user_id");
            queries.put("likes", "SELECT user_id,target_user_id FROM profile_like ORDER BY user_id,target_user_id");
            queries.put("events", "SELECT event_id,owner_user_id,title,CAST(start_date AS CHAR) start_date,CAST(start_time AS CHAR) start_time FROM calendar_event ORDER BY event_id");
            queries.put("categories", "SELECT category_id,owner_user_id,default_name FROM calendar_category WHERE is_system=FALSE ORDER BY category_id");
            queries.put("users", "SELECT user_id,role,status,email_verified FROM user ORDER BY user_id");
            queries.put("posts", "SELECT post_id,author_id,title,content,status,anonymous FROM community_post ORDER BY post_id");
            queries.put("images", "SELECT post_image_id,post_id,image_url,original_name FROM post_image ORDER BY post_image_id");
            queries.put("comments", "SELECT comment_id,post_id,author_id,parent_comment_id,content,status FROM community_comment ORDER BY comment_id");
            queries.put("postLikes", "SELECT post_id,user_id FROM post_like ORDER BY post_id,user_id");
            queries.put("commentLikes", "SELECT comment_id,user_id FROM comment_like ORDER BY comment_id,user_id");
            queries.put("rooms", "SELECT chat_room_id,category,source_post_id,source_comment_id,status FROM chat_room ORDER BY chat_room_id");
            queries.put("members", "SELECT chat_room_id,user_id,is_left,is_pinned,is_muted FROM chat_room_member ORDER BY chat_room_id,user_id");
            queries.put("messages", "SELECT message_id,chat_room_id,sender_id,content,is_read FROM chat_message ORDER BY message_id");
            queries.put("reports", "SELECT report_id,reporter_id,target_user_id,report_type,post_id,comment_id,reason,description,status,resolution_action,processed_by FROM report ORDER BY report_id");
            try (var c = guardedConnection()) {
                for (var query : queries.entrySet()) {
                    var rows = new ArrayList<Map<String,Object>>();
                    try (var s = c.createStatement(); var r = s.executeQuery(query.getValue())) {
                        while (r.next()) {
                            var row = new LinkedHashMap<String,Object>();
                            for (int i=1;i<=r.getMetaData().getColumnCount();i++) row.put(r.getMetaData().getColumnLabel(i),r.getObject(i));
                            rows.add(row);
                        }
                    }
                    snapshot.put(query.getKey(), rows);
                }
            }
            // Node decodes this IPC as UTF-8; Windows redirected System.out can otherwise use MS949.
            String projection = tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(snapshot);
            System.out.write(("E2E_SNAPSHOT " + projection + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            System.out.flush();
        }
        System.out.println("E2E_DATABASE_" + mode.toUpperCase() + "_OK");
    }
}
