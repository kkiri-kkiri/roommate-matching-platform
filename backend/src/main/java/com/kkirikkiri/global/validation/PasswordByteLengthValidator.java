package com.kkirikkiri.global.validation;

import java.nio.charset.StandardCharsets;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PasswordByteLengthValidator implements ConstraintValidator<PasswordByteLength, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || (value.length() <= 72 && value.getBytes(StandardCharsets.UTF_8).length <= 72);
    }
}
