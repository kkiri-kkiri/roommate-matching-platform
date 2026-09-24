package com.kkirikkiri.calendar.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.kkirikkiri.calendar.dto.CalendarEventCreateRequest;
import com.kkirikkiri.calendar.dto.CalendarEventListRequest;
import com.kkirikkiri.calendar.dto.CalendarEventUpdateRequest;
import com.kkirikkiri.calendar.entity.CalendarCategory;
import com.kkirikkiri.calendar.entity.CalendarEvent;
import com.kkirikkiri.calendar.entity.CalendarEventSourceType;
import com.kkirikkiri.calendar.repository.CalendarCategoryRepository;
import com.kkirikkiri.calendar.repository.CalendarCategorySettingRepository;
import com.kkirikkiri.calendar.repository.CalendarEventRepository;
import com.kkirikkiri.global.exception.CustomException;
import com.kkirikkiri.global.exception.ErrorCode;
import com.kkirikkiri.user.entity.User;
import com.kkirikkiri.user.repository.UserRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CalendarServiceDateTest {

	private final CalendarEventRepository events = mock(CalendarEventRepository.class);
	private final CalendarCategoryRepository categories = mock(CalendarCategoryRepository.class);
	private final UserRepository users = mock(UserRepository.class);
	private final User owner = mock(User.class);
	private final CalendarCategory personal = mock(CalendarCategory.class);
	private final LocalDate start = LocalDate.of(2026, 3, 3);
	private final LocalDate end = LocalDate.of(2026, 3, 20);
	private ValidatorFactory validation;
	private CalendarService service;

	@BeforeEach
	void setUp() {
		validation = Validation.buildDefaultValidatorFactory();
		service = new CalendarService(events, categories, mock(CalendarCategorySettingRepository.class),
				users, validation.getValidator());
		when(owner.getUserId()).thenReturn(1L);
		when(personal.getCategoryId()).thenReturn(4L);
		when(personal.getOwnerUser()).thenReturn(owner);
		when(personal.getCategoryCode()).thenReturn("CUSTOM-test");
		when(categories.findById(4L)).thenReturn(Optional.of(personal));
		when(users.findById(1L)).thenReturn(Optional.of(owner));
	}

	@AfterEach
	void closeValidator() {
		validation.close();
	}

	@Test
	void createsSingleDayAndPeriodUserEvents() {
		when(events.save(any(CalendarEvent.class))).thenAnswer(call -> call.getArgument(0));
		for (LocalDate last : List.of(start, end)) {
			var response = service.createEvent(1L,
					new CalendarEventCreateRequest("일정", start, last, null, null, 4L, null));
			assertEquals(start, response.startDate());
			assertEquals(last, response.endDate());
			assertTrue(response.editable());
		}
	}

	@Test
	void patchesEitherDateAndKeepsOmittedDates() {
		CalendarEvent event = userEvent();
		when(events.findById(10L)).thenReturn(Optional.of(event));
		service.updateEvent(1L, 10L, patch(start.plusDays(1), null));
		assertEquals(start.plusDays(1), event.getStartDate());
		assertEquals(end, event.getEndDate());
		service.updateEvent(1L, 10L, patch(null, end.plusDays(1)));
		assertEquals(start.plusDays(1), event.getStartDate());
		assertEquals(end.plusDays(1), event.getEndDate());
		service.updateEvent(1L, 10L, patch(null, null));
		assertEquals(end.plusDays(1), event.getEndDate());
		service.deleteEvent(1L, 10L);
		verify(events).delete(event);
	}

	@Test
	void rejectsReversedMergedDatesWithoutMutation() {
		CalendarEvent event = userEvent();
		when(events.findById(10L)).thenReturn(Optional.of(event));
		for (var request : List.of(patch(end.plusDays(1), null), patch(null, start.minusDays(1)))) {
			assertEquals(ErrorCode.VALIDATION_ERROR,
					assertThrows(CustomException.class, () -> service.updateEvent(1L, 10L, request)).getErrorCode());
			assertEquals(start, event.getStartDate());
			assertEquals(end, event.getEndDate());
		}
	}

	@Test
	void rejectsMissingOrReversedCreateDates() {
		for (var request : List.of(
				new CalendarEventCreateRequest("일정", null, end, null, null, 4L, null),
				new CalendarEventCreateRequest("일정", start, null, null, null, 4L, null),
				new CalendarEventCreateRequest("일정", end, start, null, null, 4L, null))) {
			assertEquals(ErrorCode.VALIDATION_ERROR,
					assertThrows(CustomException.class, () -> service.createEvent(1L, request)).getErrorCode());
		}
		verify(events, never()).save(any());
	}

	@Test
	void exposesPublicCrawledEventsAndRejectsUserModification() {
		var school = new CalendarCategory("SCHOOL", "학교", null, true, true);
		var crawled = new CalendarEvent(null, school, "학사일정", start, end,
				null, null, true, null, CalendarEventSourceType.CRAWLED, "학교", "url", null);
		when(events.findVisibleEvents(null, start, end)).thenReturn(List.of(crawled));
		var response = service.getEvents(null, new CalendarEventListRequest(start, end, null)).getFirst();
		assertFalse(response.editable());
		assertEquals("SCHOOL", response.category());
		when(events.findById(10L)).thenReturn(Optional.of(crawled));
		assertEquals(ErrorCode.FORBIDDEN,
				assertThrows(CustomException.class, () -> service.updateEvent(1L, 10L, patch(null, null))).getErrorCode());
		assertThrows(CustomException.class, () -> service.deleteEvent(1L, 10L));
		assertThrows(IllegalStateException.class,
				() -> crawled.update(school, "수정", start, end, null, null, true, null));
	}

	@Test
	void entityRejectsNullAndReversedDates() {
		var event = userEvent();
		assertThrows(NullPointerException.class,
				() -> event.update(personal, "일정", null, end, null, null, true, null));
		assertThrows(NullPointerException.class,
				() -> event.update(personal, "일정", start, null, null, null, true, null));
		assertThrows(IllegalArgumentException.class,
				() -> event.update(personal, "일정", end, start, null, null, true, null));
	}

	private CalendarEvent userEvent() {
		return new CalendarEvent(owner, personal, "일정", start, end, null, null, true, null,
				CalendarEventSourceType.USER, null, null, null);
	}

	private CalendarEventUpdateRequest patch(LocalDate first, LocalDate last) {
		return new CalendarEventUpdateRequest(null, first, last, null, null, null, null);
	}
}
