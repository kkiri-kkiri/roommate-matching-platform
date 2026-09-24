package com.kkirikkiri.calendar.crawler;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

@Component
public class HknuAcademicCalendarCrawler {

	public static final String SOURCE_URL = "https://www.hknu.ac.kr/kor/646/subview.do";
	public static final String SOURCE_NAME = "한경국립대학교 학사일정";
	private static final Pattern PERIOD = Pattern.compile(
			"^(\\d{2})[.-](\\d{2})(?:\\s*\\([^()]+\\))?"
			+ "(?:\\s*~\\s*(\\d{2})[.-](\\d{2})(?:\\s*\\([^()]+\\))?)?$");

	public List<CrawledAcademicEvent> crawl() {
		try {
			return parse(Jsoup.connect(SOURCE_URL).userAgent("RoommateCalendar/1.0")
					.timeout(30_000).maxBodySize(5_000_000).get());
		} catch (IOException e) {
			throw new IllegalStateException("HKNU academic calendar request failed", e);
		}
	}

	/** 실제 연간 HTML 구조 기준. 구조/날짜 오류는 전체 수집 실패로 처리한다. */
	List<CrawledAcademicEvent> parse(Document document) {
		List<CrawledAcademicEvent> events = new ArrayList<>();
		var months = document.select("#timeTableList > ul > li");
		if (months.isEmpty()) {
			throw new IllegalStateException("HKNU annual calendar structure not found");
		}
		for (Element month : months) {
			int year = Integer.parseInt(requiredText(month, ".box-year"));
			for (Element item : month.select(".list-inner > .list-box")) {
				String title = requiredText(item, ".list-content");
				var matcher = PERIOD.matcher(requiredText(item, ".list-date"));
				if (!matcher.matches()) {
					throw new IllegalStateException("Unrecognized HKNU date for: " + title);
				}
				LocalDate start = LocalDate.of(year, Integer.parseInt(matcher.group(1)),
						Integer.parseInt(matcher.group(2)));
				LocalDate end = start;
				if (matcher.group(3) != null) {
					int endMonth = Integer.parseInt(matcher.group(3));
					int endYear = endMonth < start.getMonthValue() ? year + 1 : year;
					end = LocalDate.of(endYear, endMonth, Integer.parseInt(matcher.group(4)));
				}
				events.add(new CrawledAcademicEvent(title, start, end));
			}
		}
		if (events.isEmpty()) {
			throw new IllegalStateException("HKNU academic calendar contains no events");
		}
		return List.copyOf(events);
	}

	private String requiredText(Element parent, String selector) {
		var elements = parent.select(selector);
		if (elements.size() != 1 || elements.first().text().isBlank()) {
			throw new IllegalStateException("Invalid HKNU element: " + selector);
		}
		return elements.first().text().replace('\u00a0', ' ').strip();
	}
}
