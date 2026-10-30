package com.kkirikkiri.global.init;

import static org.junit.jupiter.api.Assertions.*;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class SeedCreationPasswordTest {
    @Test void defaultRemainsRandomAndConfiguredPasswordUsesActualSignupConstraints() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var random = new SeedCreationPassword("", validator);
            String first = random.forNewAccount();
            assertNotEquals(first, random.forNewAccount());
            assertTrue(validator.validateValue(com.kkirikkiri.auth.dto.SignupRequest.class, "password", first).isEmpty());
            assertEquals("FixtureOnly-4321!", new SeedCreationPassword("FixtureOnly-4321!", validator).forNewAccount());
            for (String invalid : new String[] {" ", "short1!", "lettersOnly", "123456789!", "Fixture with spaces1!", "Aa1!".repeat(19)}) {
                var failure = assertThrows(IllegalStateException.class, () -> new SeedCreationPassword(invalid, validator).forNewAccount());
                assertEquals("Local seed creation password does not satisfy the signup password policy", failure.getMessage());
            }
        }
    }

    @Test void notAvailableInTestProductionOrMixedProfiles() {
        for (String profiles : new String[] {"test", "prod", "local,prod", "local,test"}) {
            new ApplicationContextRunner().withUserConfiguration(SeedCreationPassword.class)
                .withPropertyValues("spring.profiles.active=" + profiles)
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertTrue(context.getBeansOfType(SeedCreationPassword.class).isEmpty());
                });
        }
    }
}
