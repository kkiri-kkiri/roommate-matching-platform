package com.kkirikkiri.integration.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import com.kkirikkiri.calendar.crawler.HknuAcademicCalendarCrawler;
import com.kkirikkiri.matching.service.AiGatewayClient;

/** Real HTTP application stack with same-thread MockMvc requests and per-test rollback. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@SpringBootTest(properties = "auth.email-verification.mail-from=integration@example.invalid")
@AutoConfigureMockMvc
@ActiveProfiles(value = "test", inheritProfiles = false)
@Transactional
@MockitoBean(types = {JavaMailSender.class, AiGatewayClient.class, HknuAcademicCalendarCrawler.class})
@Import(TestUsers.class)
public @interface ApiIntegrationTest {
}
