package com.kkirikkiri.global.init;

import java.util.UUID;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import com.kkirikkiri.auth.dto.SignupRequest;

/** Local-only creation option. Never reads, resets or replaces an existing account password. */
@Component
@Profile("local & !prod & !test")
public class SeedCreationPassword {
    private final String configured;
    private final Validator validator;

    public SeedCreationPassword(@Value("${app.seed.new-user-password:}") String configured, Validator validator) {
        this.configured = configured;
        this.validator = validator;
    }

    public String forNewAccount() {
        if (configured.isEmpty()) return UUID.randomUUID() + "!Aa1";
        if (!validator.validateValue(SignupRequest.class, "password", configured).isEmpty())
            throw new IllegalStateException("Local seed creation password does not satisfy the signup password policy");
        return configured;
    }
}
