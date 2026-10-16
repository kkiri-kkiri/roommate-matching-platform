package com.kkirikkiri.auth.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import com.kkirikkiri.auth.entity.EmailVerificationPurpose;
import com.kkirikkiri.global.exception.CustomException;

class AuthMailTelemetryTest {
    @Test void classifiesErrorsAndRecordsMetricsWithoutSensitiveRawData() {
        var registry=new SimpleMeterRegistry(); var telemetry=new AuthMailTelemetry(registry);
        assertEquals("authentication",telemetry.classify(new MailAuthenticationException("SECRET-SMTP")));
        assertEquals("timeout",telemetry.classify(new MailSendException("SECRET-TOKEN",new SocketTimeoutException("SECRET-PASSWORD"))));
        assertEquals("connection",telemetry.classify(new MailSendException("SECRET-EMAIL",new ConnectException("SECRET-BODY"))));
        Logger logger=(Logger)LoggerFactory.getLogger(AuthMailTelemetry.class);
        var appender=new ListAppender<ILoggingEvent>(); appender.start(); logger.addAppender(appender);
        MDC.put("authRequestId","safe-correlation-id");
        try {
            telemetry.failure("verification",new MailAuthenticationException("SECRET-SMTP"));
            telemetry.success("verification");
            assertEquals(1,registry.get("auth.mail.delivery").tags("kind","verification","result","failure","reason","authentication").counter().count());
            assertEquals(1,registry.get("auth.mail.delivery").tags("kind","verification","result","success").counter().count());
            assertFalse(appender.list.isEmpty());
            for(var event:appender.list) {
                assertNull(event.getThrowableProxy());
                assertFalse((event.getFormattedMessage()+event.getKeyValuePairs()).contains("SECRET"));
                assertTrue(event.getKeyValuePairs().stream().anyMatch(kv->kv.key.equals("requestId") && kv.value.equals("safe-correlation-id")));
            }
        } finally { logger.detachAppender(appender); appender.stop(); MDC.remove("authRequestId"); registry.close(); }
    }
    @Test void bothSendersPreserveSanitizedErrorAndCountFailures() {
        var registry=new SimpleMeterRegistry(); var telemetry=new AuthMailTelemetry(registry);
        var smtp=mock(JavaMailSender.class);
        doThrow(new MailSendException("SECRET-BODY",new SocketTimeoutException("SECRET-TOKEN")))
            .when(smtp).send(any(SimpleMailMessage.class));
        var verification=new EmailVerificationMailSender(smtp,telemetry,"test@example.invalid");
        var reset=new PasswordResetMailSender(smtp,telemetry,"test@example.invalid");
        assertThrows(CustomException.class,()->verification.send("student@hknu.ac.kr",EmailVerificationPurpose.SIGNUP,"123456",300));
        assertThrows(CustomException.class,()->reset.send("student@hknu.ac.kr","secret-token",300));
        assertEquals(2,registry.find("auth.mail.delivery").tags("result","failure","reason","timeout").counters().stream().mapToDouble(c->c.count()).sum());
        assertThrows(CustomException.class,()->new PasswordResetMailSender(smtp,telemetry,"").send("student@hknu.ac.kr","secret-token",300));
        assertEquals(1,registry.get("auth.mail.delivery").tags("reason","configuration").counter().count());
        registry.close();
    }
    @Test void traceFilterGeneratesIdInsteadOfEchoingInputAndClearsMdc() throws Exception {
        var request=new MockHttpServletRequest("POST","/api/auth/login");
        request.addHeader("X-Request-ID","SECRET-TOKEN");
        var response=new MockHttpServletResponse();
        new AuthRequestTraceFilter().doFilter(request,response,(req,res)-> {
            assertNotEquals("SECRET-TOKEN",MDC.get("authRequestId"));
            assertEquals(response.getHeader("X-Request-ID"),MDC.get("authRequestId"));
        });
        assertNotNull(response.getHeader("X-Request-ID")); assertNull(MDC.get("authRequestId"));
    }
}
