package com.kkirikkiri.global.config;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import java.util.concurrent.atomic.AtomicBoolean;

class EnvironmentSafetyInitializerTest {
    static AtomicBoolean beanCreated=new AtomicBoolean();
    @Configuration(proxyBeanMethods=false) static class Probe {
        @Bean Object sentinel() { beanCreated.set(true); return new Object(); }
    }
    @Test void springApplicationRegistersGuardBeforeAnyBeanIsCreated() {
        for(String[] unsafe : new String[][] {
                {"--spring.profiles.active=prod,local"},
                {"--spring.profiles.active=test", "--spring.datasource.url=jdbc:mysql://example.invalid/live", "--spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver"},
                {"--spring.profiles.active=", "--spring.profiles.default=prod,local"},
                {"--spring.profiles.active=", "--spring.profiles.default=prod,test"},
                {"--spring.profiles.active=", "--spring.profiles.default=local,test"},
                {"--spring.profiles.active=", "--spring.profiles.default=test", "--spring.datasource.url=jdbc:mysql://example.invalid/live", "--spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver"}}) {
            beanCreated.set(false);
            SpringApplication app=new SpringApplication(Probe.class);app.setWebApplicationType(WebApplicationType.NONE);
            app.setRegisterShutdownHook(false);
            var arguments=new java.util.ArrayList<String>(java.util.List.of(unsafe));
            arguments.add("--spring.config.location=optional:classpath:/guard-probe.properties");
            arguments.add("--spring.config.import=");
            arguments.add("--spring.main.banner-mode=off");
            arguments.add("--spring.jpa.hibernate.ddl-auto=validate");
            arguments.add("--spring.sql.init.mode=never");
            arguments.add("--app.seed.mode=NONE");
            arguments.add("--app.calendar.crawl-on-startup=false");
            var failure=assertThrows(IllegalStateException.class,()->app.run(arguments.toArray(String[]::new)));
            assertTrue(failure.getMessage().contains("profiles must not be combined")
                    || failure.getMessage().contains("Tests require an H2 in-memory datasource"));
            assertFalse(beanCreated.get());
        }
    }
    private MockEnvironment environment(String... profiles) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(profiles);
        return env.withProperty("spring.jpa.hibernate.ddl-auto", "validate")
                .withProperty("spring.sql.init.mode", "never")
                .withProperty("app.seed.mode", "NONE")
                .withProperty("app.calendar.crawl-on-startup", "false")
                .withProperty("spring.datasource.url", "jdbc:h2:mem:safety")
                .withProperty("spring.datasource.driver-class-name", "org.h2.Driver");
    }

    @Test void rejectsMixedProfilesBeforeCreatingAnyBeans() {
        for (String[] profiles : new String[][]{{"prod","local"},{"prod","test"},{"local","test"}}) {
            try (var context = new GenericApplicationContext()) {
                context.setEnvironment(environment(profiles));
                context.registerBean("datasource", Object.class, () -> { fail("Must not create datasource"); return new Object(); });
                assertThrows(IllegalStateException.class, () -> new EnvironmentSafetyInitializer().initialize(context));
            }
        }
    }

    @ParameterizedTest @ValueSource(strings = {"jdbc:mysql://example.invalid/live", "jdbc:h2:file:live", "jdbc:h2:tcp://localhost/live"})
    void rejectsExternalAndFileTestDatabases(String url) {
        assertThrows(IllegalStateException.class, () -> EnvironmentSafetyInitializer.validate(
                environment("test").withProperty("spring.datasource.url", url)));
    }

    @ParameterizedTest @ValueSource(strings = {"jdbc:mysql://example.invalid/live", "jdbc:h2:file:live", "jdbc:h2:tcp://localhost/live"})
    void rejectsExternalAndFileDatabasesForDefaultTestProfile(String url) {
        MockEnvironment env=environment();
        env.setDefaultProfiles("test");
        env.withProperty("spring.datasource.url",url);
        assertThrows(IllegalStateException.class,()->EnvironmentSafetyInitializer.validate(env));
    }

    @Test void rejectsMixedDefaultProfiles() {
        for(String[] profiles:new String[][]{{"prod","local"},{"prod","test"},{"local","test"}}) {
            MockEnvironment env=environment();
            env.setDefaultProfiles(profiles);
            assertThrows(IllegalStateException.class,()->EnvironmentSafetyInitializer.validate(env));
        }
    }

    @Test void allowsSafeDefaultTestAndRejectsItsDangerousInitializationSettings() {
        MockEnvironment safe=environment().withProperty("spring.jpa.hibernate.ddl-auto","create-drop");
        safe.setDefaultProfiles("test");
        assertDoesNotThrow(()->EnvironmentSafetyInitializer.validate(safe));
        for(var entry:java.util.Map.of("spring.sql.init.mode","always","app.seed.mode","CREATE",
                "app.calendar.crawl-on-startup","true").entrySet()) {
            MockEnvironment env=environment().withProperty("spring.jpa.hibernate.ddl-auto","create-drop");
            env.setDefaultProfiles("test");
            env.withProperty(entry.getKey(),entry.getValue());
            assertThrows(IllegalStateException.class,()->EnvironmentSafetyInitializer.validate(env));
        }
    }

    @Test void activeProfilesOverrideInactiveDefaults() {
        MockEnvironment prod=environment("prod");prod.setDefaultProfiles("local","test");
        assertDoesNotThrow(()->EnvironmentSafetyInitializer.validate(prod));
        MockEnvironment local=environment("local").withProperty("spring.jpa.hibernate.ddl-auto","update")
                .withProperty("spring.sql.init.mode","always");local.setDefaultProfiles("prod","test");
        assertDoesNotThrow(()->EnvironmentSafetyInitializer.validate(local));
        MockEnvironment test=environment("test");test.setDefaultProfiles("prod","local");
        assertDoesNotThrow(()->EnvironmentSafetyInitializer.validate(test));
    }

    @Test void protectsDefaultAndProdButAllowsLocalDdl() {
        assertDoesNotThrow(() -> EnvironmentSafetyInitializer.validate(environment()));
        assertDoesNotThrow(() -> EnvironmentSafetyInitializer.validate(environment("prod")));
        assertThrows(IllegalStateException.class, () -> EnvironmentSafetyInitializer.validate(
                environment("prod").withProperty("spring.jpa.hibernate.ddl-auto","update")));
        assertThrows(IllegalStateException.class, () -> EnvironmentSafetyInitializer.validate(
                environment().withProperty("spring.sql.init.mode","always")));
        assertDoesNotThrow(() -> EnvironmentSafetyInitializer.validate(environment("local")
                .withProperty("spring.jpa.hibernate.ddl-auto","update").withProperty("spring.sql.init.mode","always")));
    }

    @Test void rejectsTestSqlSeedAndStartupCollection() {
        assertDoesNotThrow(() -> EnvironmentSafetyInitializer.validate(environment("test")));
        for (var entry : java.util.Map.of("spring.sql.init.mode","always","app.seed.mode","CREATE",
                "app.calendar.crawl-on-startup","true").entrySet()) {
            assertThrows(IllegalStateException.class, () -> EnvironmentSafetyInitializer.validate(
                    environment("test").withProperty(entry.getKey(),entry.getValue())));
        }
    }
}
