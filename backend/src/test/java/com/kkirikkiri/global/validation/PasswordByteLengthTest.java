package com.kkirikkiri.global.validation;

import static org.junit.jupiter.api.Assertions.*;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import com.kkirikkiri.user.dto.PasswordChangeRequest;
import com.kkirikkiri.auth.dto.PasswordResetConfirmRequest;
import com.kkirikkiri.auth.dto.LoginRequest;

class PasswordByteLengthTest {
    @Test void accepts72BytesAndRejectsAsciiAndUnicodeOverflowInAllPasswordFlows() {
        try(var factory=Validation.buildDefaultValidatorFactory()) {
            var validator=factory.getValidator();
            for(String valid:new String[]{"Aa1!"+"a".repeat(68),"Aa1!"+"가".repeat(22)+"aa"}) {
                assertTrue(validator.validate(new PasswordChangeRequest("Old1!pass",valid,valid)).isEmpty());
            }
            for(String invalid:new String[]{"Aa1!"+"a".repeat(69),"Aa1!"+"가".repeat(23)}) {
                assertFalse(validator.validate(new PasswordChangeRequest("Old1!pass",invalid,invalid)).isEmpty());
                assertFalse(validator.validate(new PasswordResetConfirmRequest("token",invalid,invalid)).isEmpty());
                assertFalse(validator.validate(new LoginRequest("student@hknu.ac.kr",invalid)).isEmpty());
            }
        }
    }
}
