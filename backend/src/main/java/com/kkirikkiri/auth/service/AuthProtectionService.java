package com.kkirikkiri.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.kkirikkiri.auth.repository.AuthLimitStore;
import com.kkirikkiri.auth.repository.AuthLimitStore.EmailTarget;
import com.kkirikkiri.global.exception.AuthRateLimitException;
import com.kkirikkiri.global.exception.ErrorCode;

@Service
@RequiredArgsConstructor
public class AuthProtectionService {
    private final AuthLimitStore store;
    private final AuthProtectionProperties properties;

    public Permit login(String email, String ip) {
        return reserveEmail(email, EmailTarget.ACCOUNT,
            subject -> rules("login", subject, ip, properties.getLoginRequests(), properties.getLoginFailures()));
    }
    public boolean emailCheck(String email, String ip) {
        EmailCheckDecision result = store.lockedEmail(email, EmailTarget.ACCOUNT,
            subject -> rules("login-check", subject, ip, properties.getLoginRequests(), null).stream().map(Rule::key).toList(),
            (subject, rows) -> new EmailCheckDecision(
                decide(rules("login-check", subject, ip, properties.getLoginRequests(), null), rows), subject.startsWith("user:")),
            value -> value.decision.error == null);
        permit(result.decision);
        return result.exists;
    }
    public Permit verify(String email, String ip) {
        return reserveEmail(email, EmailTarget.VERIFICATION,
            subject -> rules("verify", subject, ip, properties.getVerifyRequests(), properties.getVerifyFailures()));
    }
    public void reset(String email, String ip) {
        reserveEmail(email, EmailTarget.ACCOUNT, subject -> {
            var rules = rules("reset", subject, ip, properties.getResetRequests(), null);
            rules.add(request("reset-cooldown", subject, properties.getResetCooldownSeconds(), 1, ErrorCode.AUTH_REQUEST_LIMITED));
            return rules;
        });
    }
    public void send(String email, String ip, long cooldownSeconds) {
        reserveEmail(email, EmailTarget.VERIFICATION, subject -> {
            var rules = rules("send", subject, ip, properties.getSendRequests(), null);
            rules.add(request("send-cooldown", subject, cooldownSeconds, 1, ErrorCode.EMAIL_VERIFICATION_COOLDOWN));
            return rules;
        });
    }
    public void mail() {
        reserve(List.of(request("mail-global", "all", properties.getMailWindowSeconds(), properties.getMailMax(), ErrorCode.AUTH_REQUEST_LIMITED)));
    }

    private List<Rule> rules(String scope, String account, String ip, AuthProtectionProperties.Requests requests,
            AuthProtectionProperties.Failures failures) {
        var result = new ArrayList<Rule>();
        result.add(request(scope+"-email", account, requests.getWindowSeconds(), requests.getEmailMax(), ErrorCode.AUTH_REQUEST_LIMITED));
        result.add(request(scope+"-ip", ip, requests.getWindowSeconds(), requests.getIpMax(), ErrorCode.AUTH_REQUEST_LIMITED));
        if (failures != null) {
            result.add(new Rule(key(scope+"-email-fail", account), failures.getWindowSeconds()*1000L, 0,
                failures.getEmailMax(), failures.getBlockSeconds()*1000L, true, ErrorCode.AUTH_REQUEST_LIMITED));
            result.add(new Rule(key(scope+"-ip-fail", ip), failures.getWindowSeconds()*1000L, 0,
                failures.getIpMax(), failures.getBlockSeconds()*1000L, false, ErrorCode.AUTH_REQUEST_LIMITED));
        }
        return result;
    }

    private Permit reserve(List<Rule> rules) {
        Decision decision = store.locked(rules.stream().map(Rule::key).toList(),
            rows -> decide(rules, rows), result -> result.error == null);
        return permit(decision);
    }

    private Permit reserveEmail(String email, EmailTarget target, Function<String, List<Rule>> rules) {
        Decision decision = store.lockedEmail(email, target,
            subject -> rules.apply(subject).stream().map(Rule::key).toList(),
            (subject, rows) -> decide(rules.apply(subject), rows), result -> result.error == null);
        return permit(decision);
    }

    private Decision decide(List<Rule> rules, List<AuthLimitStore.Bucket> rows) {
            Map<String, Rule> byKey = rules.stream().collect(Collectors.toMap(Rule::key, Function.identity()));
            long now = store.now();
            long retry = 0;
            ErrorCode error = ErrorCode.AUTH_REQUEST_LIMITED;
            for (var row : rows) {
                Rule rule = byKey.get(row.key);
                // A block cannot be extended by rejected traffic. Expiry restores access.
                if (row.blocked > now) {
                    retry = Math.max(retry, row.blocked-now);
                    continue;
                }
                if (row.started == 0 || now-row.started >= rule.windowMillis || row.blocked != 0) {
                    row.started=now; row.generation++; row.requests=0; row.failures=0; row.pending=0; row.blocked=0;
                }
                if ((rule.maxRequests > 0 && row.requests >= rule.maxRequests)
                        || (rule.maxFailures > 0 && row.failures+row.pending >= rule.maxFailures)) {
                    retry=Math.max(retry, Math.max(1000, row.started+rule.windowMillis-now));
                    if (rule.error == ErrorCode.EMAIL_VERIFICATION_COOLDOWN) error=rule.error;
                }
            }
            if (retry > 0) return new Decision(null, error, (retry+999)/1000);
            var reservations = new ArrayList<Reservation>();
            for (var row : rows) {
                Rule rule=byKey.get(row.key);
                if (rule.maxRequests > 0) row.requests++;
                if (rule.maxFailures > 0) {
                    row.pending++;
                    reservations.add(new Reservation(rule, row.generation, row.started));
                }
            }
            return new Decision(new Permit(List.copyOf(reservations)), null, 0);
    }

    private Permit permit(Decision decision) {
        if (decision.error != null) throw new AuthRateLimitException(decision.error, decision.retrySeconds);
        return decision.permit;
    }

    /** Runs after the business transaction, including rollback; no raw credentials enter this store. */
    public void complete(Permit permit, boolean success) {
        if (permit == null || permit.reservations.isEmpty()) return;
        var byKey=permit.reservations.stream().collect(Collectors.toMap(r -> r.rule.key, Function.identity()));
        store.locked(new ArrayList<>(byKey.keySet()), rows -> {
            long now=store.now();
            for (var row : rows) {
                Reservation reservation=byKey.get(row.key);
                if (row.generation != reservation.generation || row.started != reservation.started) continue;
                row.pending=Math.max(0, row.pending-1);
                Rule rule=reservation.rule;
                if (success && rule.resetOnSuccess) row.failures=0;
                if (!success && ++row.failures >= rule.maxFailures && row.blocked == 0) row.blocked=now+rule.blockMillis;
            }
            return null;
        });
    }
    private Rule request(String scope, String subject, long seconds, int max, ErrorCode error) {
        return new Rule(key(scope, subject), seconds*1000L, max, 0, 0, false, error);
    }
    private String key(String scope, String subject) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest((scope+":"+subject).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
    private record Rule(String key, long windowMillis, int maxRequests, int maxFailures, long blockMillis,
                        boolean resetOnSuccess, ErrorCode error) { }
    private record Reservation(Rule rule, long generation, long started) { }
    public record Permit(List<Reservation> reservations) { }
    private record Decision(Permit permit, ErrorCode error, long retrySeconds) { }
    private record EmailCheckDecision(Decision decision, boolean exists) { }
}
