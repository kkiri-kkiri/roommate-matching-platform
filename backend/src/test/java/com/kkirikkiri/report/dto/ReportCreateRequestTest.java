package com.kkirikkiri.report.dto;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ReportCreateRequestTest {
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
}
