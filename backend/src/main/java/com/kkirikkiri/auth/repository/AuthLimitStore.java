package com.kkirikkiri.auth.repository;

import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;
import java.util.Objects;
import java.sql.SQLException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.function.Function;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.support.JdbcTransactionManager;
import com.kkirikkiri.auth.service.AuthDatabaseRetryProperties;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/** Transactions never hold these locks across BCrypt, business writes or SMTP. */
@Repository
public class AuthLimitStore {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final TransactionTemplate withoutBusinessTransaction;
    private final AuthDatabaseRetryProperties retry;

    public AuthLimitStore(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this(jdbc, manager, new AuthDatabaseRetryProperties());
    }

    @Autowired
    public AuthLimitStore(JdbcTemplate jdbc, PlatformTransactionManager manager, AuthDatabaseRetryProperties retry) {
        this.jdbc = jdbc;
        this.retry = retry;
        // Suspend the JPA resource first: a JDBC manager alone must never reuse/commit an outer JPA connection.
        withoutBusinessTransaction = new TransactionTemplate(manager);
        withoutBusinessTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
        tx = new TransactionTemplate(new JdbcTransactionManager(Objects.requireNonNull(jdbc.getDataSource())));
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        tx.setTimeout(5);
        verifyEmailCollations();
    }

    public <T> T locked(List<String> keys, Function<List<Bucket>, T> work) {
        return locked(keys, work, result -> true);
    }

    public <T> T locked(List<String> keys, Function<List<Bucket>, T> work, Predicate<T> commit) {
        return withoutBusinessTransaction.execute(ignored -> retryTransaction(() -> tx.execute(status -> lockedInTransaction(keys, work, commit, status))));
    }

    public enum EmailTarget { ACCOUNT, VERIFICATION }

    /** Identity creation and denied reservations roll back together, including first-use races. */
    public <T> T lockedEmail(String email, EmailTarget target, Function<String, List<String>> keys,
            BiFunction<String, List<Bucket>, T> work, Predicate<T> commit) {
        return withoutBusinessTransaction.execute(ignored -> retryTransaction(() -> tx.execute(status -> {
            String subject = emailSubject(email, target);
            return lockedInTransaction(keys.apply(subject), rows -> work.apply(subject, rows), commit, status);
        })));
    }

    private <T> T retryTransaction(Supplier<T> attempt) {
        for (int number = 1; ; number++) {
            try {
                return attempt.get();
            } catch (RuntimeException failure) {
                if (number >= retry.getMaxAttempts() || !retryableLockFailure(failure)) throw failure;
                // TransactionTemplate has already rolled back and released its connection here.
                long delay = Math.min(retry.getMaxDelayMillis(), retry.getInitialDelayMillis() * (1L << (number - 1)));
                delay += ThreadLocalRandom.current().nextLong(retry.getJitterMillis() + 1);
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw failure;
                }
            }
        }
    }

    private boolean retryableLockFailure(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && (sql.getErrorCode() == 1213 || sql.getErrorCode() == 1205)) return true;
        }
        return false;
    }

    private String emailSubject(String email, EmailTarget target) {
        if (target == EmailTarget.ACCOUNT) {
            var users = jdbc.queryForList("SELECT user_id FROM `user` WHERE school_email = ?", Long.class, email);
            if (!users.isEmpty()) return "user:" + users.getFirst();
        }
        // The two columns deliberately retain the two source columns' possibly different collations.
        String column = target == EmailTarget.ACCOUNT ? "account_email" : "verification_email";
        var existing = jdbc.queryForList("SELECT identity_id FROM auth_email_identity WHERE " + column + " = ?", Long.class, email);
        if (!existing.isEmpty()) return (target == EmailTarget.ACCOUNT ? "account-email:" : "verification-email:") + existing.getFirst();
        // Identities are immutable. Existing subjects need no write lock, especially while a send holds its mutex.
        jdbc.update("INSERT INTO auth_email_identity (" + column + ") VALUES (?) "
                + "ON DUPLICATE KEY UPDATE identity_id = identity_id", email);
        Long id = jdbc.queryForObject("SELECT identity_id FROM auth_email_identity WHERE " + column + " = ?", Long.class, email);
        return (target == EmailTarget.ACCOUNT ? "account-email:" : "verification-email:") + id;
    }

    private void verifyEmailCollations() {
        String product = jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<String>)
                connection -> connection.getMetaData().getDatabaseProductName());
        if (!"MySQL".equals(product)) return; // H2 behavior is covered separately; it cannot emulate MySQL UCA.
        for (String[] pair : List.of(new String[]{"user", "school_email", "account_email"},
                new String[]{"email_verification", "email", "verification_email"})) {
            var source = jdbc.queryForList("""
                SELECT CHARACTER_SET_NAME, COLLATION_NAME, COLUMN_TYPE FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?
                """, pair[0], pair[1]);
            var identity = jdbc.queryForList("""
                SELECT CHARACTER_SET_NAME, COLLATION_NAME, COLUMN_TYPE FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'auth_email_identity' AND column_name = ?
                """, pair[2]);
            if (source.size() != 1 || !source.equals(identity)) {
                throw new IllegalStateException("Auth email identity schema must match source email comparison rules");
            }
        }
    }

    private <T> T lockedInTransaction(List<String> keys, Function<List<Bucket>, T> work,
            Predicate<T> commit, TransactionStatus status) {
            // Acquire PK locks in one sorted upsert, then read the locked rows in one round trip.
            // Reduces connection occupancy/convoys on shared IPs without changing quotas or isolation.
            List<String> sorted = keys.stream().distinct().sorted().toList();
            List<Bucket> rows;
            if (sorted.isEmpty()) {
                rows = List.of();
            } else {
                jdbc.update("""
                    INSERT INTO auth_limit_bucket
                    (bucket_key, window_started_at, generation, request_count, failure_count, in_flight, blocked_until, updated_at)
                    VALUES
                    """ + String.join(",", Collections.nCopies(sorted.size(), "(?,0,0,0,0,0,0,0)"))
                    + " ON DUPLICATE KEY UPDATE bucket_key = bucket_key", sorted.toArray());
                rows = jdbc.query("SELECT * FROM auth_limit_bucket WHERE bucket_key IN ("
                    + String.join(",", Collections.nCopies(sorted.size(), "?")) + ") ORDER BY bucket_key FOR UPDATE",
                    (rs, row) -> new Bucket(rs.getString("bucket_key"), rs.getLong("window_started_at"), rs.getLong("generation"),
                        rs.getInt("request_count"), rs.getInt("failure_count"), rs.getInt("in_flight"),
                        rs.getLong("blocked_until")), sorted.toArray());
            }
            T result = work.apply(rows);
            if (!commit.test(result)) {
                status.setRollbackOnly(); // Denied IPs cannot grow the table by inventing new emails.
                return result;
            }
            long now = now();
            for (Bucket row : rows) {
                jdbc.update("""
                    UPDATE auth_limit_bucket SET window_started_at=?, generation=?, request_count=?,
                    failure_count=?, in_flight=?, blocked_until=?, updated_at=? WHERE bucket_key=?
                    """, row.started, row.generation, row.requests, row.failures, row.pending, row.blocked, now, row.key);
            }
            return result;
    }

    public long now() {
        // All nodes use DB time. MySQL sessions must use UTC (see deployment instructions).
        return jdbc.queryForObject("SELECT CURRENT_TIMESTAMP(3)", (rs, row) ->
            rs.getTimestamp(1, Calendar.getInstance(TimeZone.getTimeZone("UTC"))).getTime());
    }

    public static class Bucket {
        public final String key;
        public long started, generation, blocked;
        public int requests, failures, pending;
        Bucket(String key, long started, long generation, int requests, int failures, int pending, long blocked) {
            this.key=key; this.started=started; this.generation=generation; this.requests=requests;
            this.failures=failures; this.pending=pending; this.blocked=blocked;
        }
    }
}
