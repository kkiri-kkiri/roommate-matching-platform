package com.kkirikkiri.report.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import com.kkirikkiri.user.entity.User;

class ReportTest {
    private final User reporter = new User("encoded", "reporter@example.com");
    private final User target = new User("encoded", "target@example.com");
    private final User admin = new User("encoded", "admin@example.com");
    private final LocalDateTime createdAt = LocalDateTime.of(2026, 10, 1, 12, 0);

    @ParameterizedTest
    @ValueSource(strings = {"욕설·비방", "광고·홍보", "음란성 콘텐츠", "개인정보 노출", "도배·스팸"})
    void acceptsSupportedReasonsWithoutDescription(String reason) {
        // when
        Report report = Report.forUser(reporter, target, reason, null);
        // then
        assertEquals(reason, report.getReason());
        assertEquals(ReportStatus.PENDING, report.getStatus());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "미지원 사유"})
    void rejectsUnsupportedReasons(String reason) {
        // when / then
        assertThrows(IllegalArgumentException.class, () -> Report.forUser(reporter, target, reason, null));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void rejectsOtherReasonWithoutDescription(String description) {
        // when / then
        assertThrows(IllegalArgumentException.class, () -> Report.forUser(reporter, target, "기타", description));
    }

    @ParameterizedTest
    @EnumSource(value = ReportStatus.class, names = {"COMPLETED", "REJECTED"})
    void processesPendingReportAndRecordsDecision(ReportStatus status) {
        // given
        Report report = pendingReport();
        LocalDateTime processedAt = createdAt.plusMinutes(1);
        // when
        report.process(status, "NONE", admin, processedAt);
        // then
        assertEquals(status, report.getStatus());
        assertEquals("NONE", report.getResolutionAction());
        assertSame(admin, report.getProcessedBy());
        assertEquals(processedAt, report.getProcessedAt());
    }

    @ParameterizedTest
    @EnumSource(value = ReportStatus.class, names = {"COMPLETED", "REJECTED"})
    void rejectsReprocessingAndPreservesOriginalDecision(ReportStatus status) {
        // given
        Report report = pendingReport();
        report.process(status, "NONE", admin, createdAt);
        // when / then
        assertThrows(IllegalStateException.class,
                () -> report.process(ReportStatus.COMPLETED, "DELETE", reporter, createdAt.plusMinutes(1)));
        assertEquals(status, report.getStatus());
        assertEquals("NONE", report.getResolutionAction());
        assertSame(admin, report.getProcessedBy());
        assertEquals(createdAt, report.getProcessedAt());
    }

    @Test
    void rejectsPendingAsProcessingResultWithoutChangingReport() {
        // given
        Report report = pendingReport();
        // when / then
        assertThrows(IllegalArgumentException.class, () -> report.process(ReportStatus.PENDING, "NONE", admin, createdAt));
        assertUnprocessed(report);
    }

    @ParameterizedTest
    @ValueSource(strings = {"status", "processedBy", "processedAt"})
    void requiresProcessingStatusActorAndTimestamp(String missing) {
        // given
        Report report = pendingReport();
        // when / then
        assertThrows(NullPointerException.class, () -> report.process(
                missing.equals("status") ? null : ReportStatus.COMPLETED, null,
                missing.equals("processedBy") ? null : admin,
                missing.equals("processedAt") ? null : createdAt));
        assertUnprocessed(report);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {30})
    void acceptsOptionalActionAndMaximumActionLength(Integer length) {
        // given
        Report report = pendingReport();
        String action = length == null ? null : "a".repeat(length);
        // when
        report.process(ReportStatus.COMPLETED, action, admin, createdAt);
        // then
        assertEquals(ReportStatus.COMPLETED, report.getStatus());
        assertEquals(action, report.getResolutionAction());
    }

    @Test
    void rejectsActionExceedingThirtyCharactersWithoutChangingReport() {
        // given
        Report report = pendingReport();
        // when / then
        assertThrows(IllegalArgumentException.class,
                () -> report.process(ReportStatus.COMPLETED, "a".repeat(31), admin, createdAt));
        assertUnprocessed(report);
    }

    @Test
    void acceptsProcessingAtCreationTime() {
        // given
        Report report = pendingReport();
        // when
        report.process(ReportStatus.COMPLETED, null, admin, createdAt);
        // then
        assertEquals(ReportStatus.COMPLETED, report.getStatus());
        assertEquals(createdAt, report.getProcessedAt());
    }

    @Test
    void rejectsProcessingBeforeCreationWithoutChangingReport() {
        // given
        Report report = pendingReport();
        // when / then
        assertThrows(IllegalArgumentException.class,
                () -> report.process(ReportStatus.COMPLETED, null, admin, createdAt.minusNanos(1)));
        assertUnprocessed(report);
    }

    private Report pendingReport() {
        Report report = Report.forUser(reporter, target, "욕설·비방", null);
        ReflectionTestUtils.setField(report, "createdAt", createdAt);
        return report;
    }

    private void assertUnprocessed(Report report) {
        assertEquals(ReportStatus.PENDING, report.getStatus());
        assertNull(report.getResolutionAction());
        assertNull(report.getProcessedBy());
        assertNull(report.getProcessedAt());
    }
}
