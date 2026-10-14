package com.kkirikkiri.auth.repository;

import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;
import java.util.function.Function;
import java.util.function.Predicate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Transactions never hold these locks across BCrypt, business writes or SMTP. */
@Repository
public class AuthLimitStore {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public AuthLimitStore(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        tx = new TransactionTemplate(manager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        tx.setTimeout(5);
    }

    public <T> T locked(List<String> keys, Function<List<Bucket>, T> work) {
        return locked(keys, work, result -> true);
    }

    public <T> T locked(List<String> keys, Function<List<Bucket>, T> work, Predicate<T> commit) {
        return tx.execute(status -> {
            // Sorted inserts/locks also serialize creation of previously absent buckets.
            List<Bucket> rows = keys.stream().distinct().sorted().map(key -> {
                jdbc.update("""
                    INSERT INTO auth_limit_bucket
                    (bucket_key, window_started_at, generation, request_count, failure_count, in_flight, blocked_until, updated_at)
                    VALUES (?, 0, 0, 0, 0, 0, 0, 0)
                    ON DUPLICATE KEY UPDATE bucket_key = bucket_key
                    """, key);
                return jdbc.queryForObject("SELECT * FROM auth_limit_bucket WHERE bucket_key = ? FOR UPDATE",
                    (rs, row) -> new Bucket(key, rs.getLong("window_started_at"), rs.getLong("generation"),
                        rs.getInt("request_count"), rs.getInt("failure_count"), rs.getInt("in_flight"),
                        rs.getLong("blocked_until")), key);
            }).toList();
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
        });
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
