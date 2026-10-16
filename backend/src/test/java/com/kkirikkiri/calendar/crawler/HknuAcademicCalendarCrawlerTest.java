package com.kkirikkiri.calendar.crawler;

import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDate;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

class HknuAcademicCalendarCrawlerTest {
    private final HknuAcademicCalendarCrawler crawler = new HknuAcademicCalendarCrawler();
    @Test void parsesFixedAnnualFixtureWithoutHttp() throws Exception {
        try (var stream = getClass().getResourceAsStream("/calendar/hknu-2026.html")) {
            assertNotNull(stream);
            var events = crawler.parse(Jsoup.parse(stream, "UTF-8", ""));
            assertEquals(62, events.size());
            assertTrue(events.stream().allMatch(e -> !e.title().isBlank() && !e.endDate().isBefore(e.startDate())));
        }
    }
    @Test void handlesYearBoundary() {
        var events = crawler.parse(Jsoup.parse(html("12.31 ~ 01.02")));
        assertEquals(LocalDate.of(2026, 12, 31), events.getFirst().startDate());
        assertEquals(LocalDate.of(2027, 1, 2), events.getFirst().endDate());
    }
    @Test void failsClosedOnBrokenStructureAndDates() {
        assertThrows(IllegalStateException.class, () -> crawler.parse(Jsoup.parse("<html/>")));
        assertThrows(IllegalStateException.class, () -> crawler.parse(Jsoup.parse(html("unknown"))));
        assertThrows(java.time.DateTimeException.class, () -> crawler.parse(Jsoup.parse(html("02.30"))));
    }
    private String html(String period) {
        return "<div id='timeTableList'><ul><li><p class='box-year'>2026</p><div class='list-inner'><div class='list-box'>"
                + "<p class='list-date'>" + period + "</p><p class='list-content'>fixture</p></div></div></li></ul></div>";
    }
}
