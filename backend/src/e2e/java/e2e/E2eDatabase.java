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
        check(Set.of("prepare", "reset", "clean", "verify").contains(mode), "Unknown E2E fixture operation");
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
                    em.persist(new UserProfile(user, "E2E사용자", "남자", dorm, faculty, null, (short)2000, "20", "1학년", "INTJ"));
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
        System.out.println("E2E_DATABASE_" + mode.toUpperCase() + "_OK");
    }
}
