package com.kkirikkiri.global.config;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/** Runs before any datasource, Hibernate DDL or ApplicationRunner can be created. */
public class EnvironmentSafetyInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    @Override
    public void initialize(ConfigurableApplicationContext context) {
        validate(context.getEnvironment());
    }

    public static void validate(Environment environment) {
        // Spring falls back to default profiles only when no active profiles are configured.
        boolean local = environment.acceptsProfiles(Profiles.of("local"));
        boolean test = environment.acceptsProfiles(Profiles.of("test"));
        boolean prod = environment.acceptsProfiles(Profiles.of("prod"));
        if ((prod && (local || test)) || (local && test)) {
            throw new IllegalStateException("prod, local and test profiles must not be combined");
        }
        if (test) {
            String url = environment.getProperty("spring.datasource.url", "");
            if (!url.startsWith("jdbc:h2:mem:")
                    || !"org.h2.Driver".equals(environment.getProperty("spring.datasource.driver-class-name"))) {
                throw new IllegalStateException("Tests require an H2 in-memory datasource");
            }
            require(environment, "spring.sql.init.mode", "never");
            require(environment, "app.seed.mode", "NONE");
            require(environment, "app.calendar.crawl-on-startup", "false");
        } else if (!local) {
            require(environment, "spring.jpa.hibernate.ddl-auto", "validate");
            require(environment, "spring.sql.init.mode", "never");
        }
    }

    private static void require(Environment environment, String name, String expected) {
        if (!expected.equalsIgnoreCase(environment.getProperty(name, ""))) {
            throw new IllegalStateException("Unsafe environment setting: " + name);
        }
    }
}
