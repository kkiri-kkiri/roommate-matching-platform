package com.kkirikkiri.report.dto;

import static org.junit.jupiter.api.Assertions.*;

import java.util.stream.Stream;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ReportCreateRequestTest {
    static ValidatorFactory factory;
    static Validator validator;

    @BeforeAll static void validator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll static void closeValidator() { factory.close(); }
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void requiresNonBlankDescriptionForOtherReason(String description) {
        // when / then
        assertFalse(new ReportCreateRequest("USER", 1L, "기타", description).isDescriptionValid());
    }

    @Test
    void acceptsNonBlankDescriptionForOtherReason() {
        // when / then
        assertTrue(new ReportCreateRequest("USER", 1L, "기타", " 상세 내용 ").isDescriptionValid());
    }

    @ParameterizedTest
    @ValueSource(strings = {"욕설·비방", "광고·홍보", "음란성 콘텐츠", "개인정보 노출", "도배·스팸"})
    void allowsOmittedDescriptionForSpecificReasons(String reason) {
        // when / then
        assertTrue(new ReportCreateRequest("USER", 1L, reason, null).isDescriptionValid());
    }

    @ParameterizedTest
    @MethodSource("descriptionBoundaries")
    void beanValidationCountsUtf16LikeFrontendMaxLength(String unit, int length) {
        String description = unit.repeat(length / unit.length()) + "a".repeat(length % unit.length());
        var violations = validator.validate(new ReportCreateRequest("USER", 1L, "기타", description));
        assertEquals(length <= 500, violations.isEmpty());
        if (length > 500) assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("description")));
    }

    static Stream<Arguments> descriptionBoundaries() {
        return Stream.of("a", "가", "😀").flatMap(unit -> Stream.of(499, 500, 501, 70000)
                .map(length -> Arguments.of(unit, length)));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void beanValidationPreservesExistingNullAndBlankPolicies(String description) {
        assertTrue(validator.validate(new ReportCreateRequest("USER", 1L, "욕설·비방", description)).isEmpty());
        assertFalse(validator.validate(new ReportCreateRequest("USER", 1L, "기타", description)).isEmpty());
    }
}
