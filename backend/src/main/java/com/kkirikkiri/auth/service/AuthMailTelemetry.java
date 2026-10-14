package com.kkirikkiri.auth.service;

import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.UUID;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.stereotype.Component;

@Component
public class AuthMailTelemetry {
    private static final Logger log=LoggerFactory.getLogger(AuthMailTelemetry.class);
    private final MeterRegistry meters;
    public AuthMailTelemetry(MeterRegistry meters) { this.meters=meters; }

    public void success(String kind) {
        meters.counter("auth.mail.delivery", "kind", kind, "result", "success", "reason", "none").increment();
        log.atInfo().addKeyValue("event", "auth_mail").addKeyValue("kind", kind)
            .addKeyValue("result", "success").addKeyValue("requestId", trace()).log("Auth mail delivery succeeded");
    }
    public void failure(String kind, Throwable error) { failure(kind, classify(error)); }
    public void failure(String kind, String reason) {
        meters.counter("auth.mail.delivery", "kind", kind, "result", "failure", "reason", reason).increment();
        // Deliberately omit message, stack trace, recipient and mail contents.
        log.atWarn().addKeyValue("event", "auth_mail").addKeyValue("kind", kind)
            .addKeyValue("result", "failure").addKeyValue("reason", reason)
            .addKeyValue("requestId", trace()).log("Auth mail delivery failed");
    }
    private String trace() {
        String id=MDC.get("authRequestId");
        return id == null ? UUID.randomUUID().toString() : id;
    }
    String classify(Throwable error) {
        Set<Throwable> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        return classify(error, seen, 0);
    }
    private String classify(Throwable error, Set<Throwable> seen, int depth) {
        if (error == null || depth > 20 || !seen.add(error)) return "other";
        if (error instanceof MailAuthenticationException || error instanceof jakarta.mail.AuthenticationFailedException) return "authentication";
        if (error instanceof SocketTimeoutException) return "timeout";
        if (error instanceof ConnectException || error instanceof UnknownHostException || error instanceof SocketException) return "connection";
        String cause=classify(error.getCause(), seen, depth+1);
        if (!cause.equals("other")) return cause;
        if (error instanceof MailSendException send) {
            for (Exception nested : send.getMessageExceptions()) {
                String reason=classify(nested, seen, depth+1);
                if (!reason.equals("other")) return reason;
            }
        }
        if (error instanceof jakarta.mail.MessagingException mail && mail.getNextException() != null)
            return classify(mail.getNextException(), seen, depth+1);
        return "other";
    }
}
