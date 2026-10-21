package e2e;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;
import org.springframework.boot.SpringApplication;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.SimpleMailMessage;
import jakarta.mail.internet.MimeMessage;
import com.kkirikkiri.BackendApplication;
import com.kkirikkiri.matching.service.AiGatewayClient;
import com.kkirikkiri.matching.dto.AiProfileAnalysisInput;
import tools.jackson.databind.ObjectMapper;

/** Production HTTP/auth/JPA stack with test-only infrastructure, outside the production source set. */
public final class E2eServer {
    public static void main(String[] args) throws Exception {
        try (var ignored = E2eDatabase.guardedConnection()) { /* before Spring can touch the DB */ }
        String ttl = Objects.requireNonNullElse(System.getenv("E2E_ACCESS_TTL"), "3600");
        E2eDatabase.check(Set.of("8", "3600").contains(ttl), "Unsafe E2E token lifetime");
        Map<String, String> settings = new HashMap<>(Map.ofEntries(
            Map.entry("spring.config.location", "classpath:/application.properties"),
            Map.entry("spring.config.import", ""), Map.entry("spring.profiles.active", "e2e"),
            Map.entry("spring.profiles.include", ""), Map.entry("server.address", "127.0.0.1"),
            Map.entry("server.port", "18080"), Map.entry("server.forward-headers-strategy", "NONE"),
            Map.entry("spring.datasource.url", E2eDatabase.url()),
            Map.entry("spring.datasource.username", E2eDatabase.account()),
            Map.entry("spring.datasource.password", E2eDatabase.required("E2E_DB_PASSWORD")),
            Map.entry("spring.jpa.hibernate.ddl-auto", "validate"), Map.entry("spring.sql.init.mode", "never"),
            Map.entry("app.seed.mode", "NONE"), Map.entry("app.calendar.crawl-on-startup", "false"),
            Map.entry("app.community.image-cleanup.enabled", "false"),
            Map.entry("app.community.image-directory", E2eDatabase.required("E2E_IMAGE_DIRECTORY")),
            Map.entry("app.cors.allowed-origins", "http://127.0.0.1:13000"),
            Map.entry("jwt.secret", E2eDatabase.required("E2E_JWT_SECRET")),
            Map.entry("jwt.access-token-expiration-seconds", ttl),
            Map.entry("ai.api-key", "e2e-only"), Map.entry("ai.base-url", "http://127.0.0.1:1"),
            Map.entry("ai.model", "e2e-only"), Map.entry("spring.mail.host", "127.0.0.1"),
            Map.entry("spring.mail.port", "1"), Map.entry("spring.mail.username", ""),
            Map.entry("spring.mail.password", ""), Map.entry("spring.mail.test-connection", "false"),
            Map.entry("auth.email-verification.mail-from", "e2e@example.invalid"),
            Map.entry("logging.level.root", "WARN"),
            Map.entry("logging.level.org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration", "OFF"),
            Map.entry("spring.main.banner-mode", "off")));
        settings.forEach(System::setProperty);
        var app = new SpringApplication(BackendApplication.class, Infrastructure.class);
        try (var context = app.run()) {
            E2eDatabase.check(Arrays.equals(context.getEnvironment().getActiveProfiles(), new String[]{"e2e"}), "Unsafe E2E profiles");
            E2eDatabase.check(context.getBeansOfType(org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor.class).isEmpty(), "Scheduling still enabled");
            E2eDatabase.check(context.getBeansOfType(org.springframework.boot.ApplicationRunner.class).isEmpty(), "Unexpected production seed runner");
            E2eDatabase.check(context.getBean(JavaMailSender.class) instanceof MemoryMail, "Real SMTP configured");
            E2eDatabase.check(context.getBean(AiGatewayClient.class) instanceof FixedAi, "Real AI configured");
            E2eDatabase.check(context.getBean(com.kkirikkiri.calendar.crawler.HknuAcademicCalendarCrawler.class)
                instanceof DisabledCrawler, "Real school crawler configured");
            System.out.println("E2E_BACKEND_READY"); System.out.flush();
            // EOF also closes Spring when the owning Node process disappears.
            try (var reader = new BufferedReader(new InputStreamReader(System.in))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(" ", 3);
                    if (parts.length == 3 && parts[0].equals("MAIL") && parts[1].matches("[a-f0-9]{24}")) {
                        String code = ((MemoryMail)context.getBean(JavaMailSender.class)).codes.getOrDefault(parts[2], "");
                        // Private parent-process pipe; the runner consumes these lines without writing logs.
                        System.out.println("E2E_PRIVATE " + parts[1] + " " + code); System.out.flush();
                    }
                }
            }
        }
    }
    @Configuration(proxyBeanMethods = false)
    @Profile("e2e & !prod & !local & !test")
    public static class Infrastructure {
        @Bean static BeanFactoryPostProcessor preventScheduledWork() {
            return factory -> {
                var registry = (DefaultListableBeanFactory)factory;
                registry.removeBeanDefinition("aiGatewayClient");
                registry.removeBeanDefinition("hknuAcademicCalendarCrawler");
                for (Class<?> type : List.of(org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor.class,
                        com.kkirikkiri.calendar.scheduler.CalendarCrawlingScheduler.class,
                        com.kkirikkiri.community.post.service.PostImageCleanupScheduler.class)) {
                    for (String name : registry.getBeanNamesForType(type, false, false)) {
                        if (registry.containsBeanDefinition(name)) registry.removeBeanDefinition(name);
                    }
                }
            };
        }
        @Bean JavaMailSender memoryMail() { return new MemoryMail(); }
        @Bean @Primary AiGatewayClient fixedAi(ObjectMapper mapper) { return new FixedAi(mapper); }
        @Bean com.kkirikkiri.calendar.crawler.HknuAcademicCalendarCrawler disabledCrawler() { return new DisabledCrawler(); }
    }
    static final class MemoryMail extends JavaMailSenderImpl {
        final Map<String,String> codes = new java.util.concurrent.ConcurrentHashMap<>();
        @Override public void send(SimpleMailMessage... messages) {
            for (var message : messages) {
                var matcher = java.util.regex.Pattern.compile("인증번호: (\\d{6})").matcher(Objects.requireNonNullElse(message.getText(), ""));
                if (matcher.find() && message.getTo() != null) for (String recipient : message.getTo()) codes.put(recipient, matcher.group(1));
            }
        }
        @Override public void send(MimeMessage... messages) { /* no network, no sensitive logs */ }
    }
    static final class FixedAi extends AiGatewayClient {
        FixedAi(ObjectMapper mapper) { super(mapper, "", "", ""); }
        @Override public String analyze(AiProfileAnalysisInput input) { return "E2E 테스트용 고정 분석입니다."; }
    }
    static final class DisabledCrawler extends com.kkirikkiri.calendar.crawler.HknuAcademicCalendarCrawler {
        @Override public List<com.kkirikkiri.calendar.crawler.CrawledAcademicEvent> crawl() {
            throw new IllegalStateException("School crawling is disabled in E2E");
        }
    }
}
