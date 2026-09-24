package com.kkirikkiri.calendar.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.kkirikkiri.calendar.dto.CalendarCategoryCreateRequest;
import com.kkirikkiri.calendar.dto.CalendarCategoryUpdateRequest;
import com.kkirikkiri.calendar.dto.CalendarEventCreateRequest;
import com.kkirikkiri.calendar.entity.CalendarCategory;
import com.kkirikkiri.calendar.entity.CalendarCategorySetting;
import com.kkirikkiri.calendar.repository.CalendarCategoryRepository;
import com.kkirikkiri.calendar.repository.CalendarCategorySettingRepository;
import com.kkirikkiri.calendar.repository.CalendarEventRepository;
import com.kkirikkiri.global.exception.CustomException;
import com.kkirikkiri.global.exception.ErrorCode;
import com.kkirikkiri.user.entity.User;
import com.kkirikkiri.user.repository.UserRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CalendarCategoryPolicyTest {

	private final CalendarCategoryRepository categories = mock(CalendarCategoryRepository.class);
	private final CalendarCategorySettingRepository settings = mock(CalendarCategorySettingRepository.class);
	private final CalendarEventRepository events = mock(CalendarEventRepository.class);
	private final UserRepository users = mock(UserRepository.class);
	private final User owner = mock(User.class);
	private ValidatorFactory validation;
	private CalendarService service;

	@BeforeEach
	void setUp() {
		validation = Validation.buildDefaultValidatorFactory();
		service = new CalendarService(events, categories, settings, users, validation.getValidator());
		when(owner.getUserId()).thenReturn(1L);
		when(users.findById(1L)).thenReturn(Optional.of(owner));
	}

	@AfterEach
	void closeValidation() {
		validation.close();
	}

	@Test
	void customCategoryCanBeCreatedUpdatedAndDeletedByOwner() {
		when(categories.save(any())).thenAnswer(call -> {
			CalendarCategory category = call.getArgument(0);
			ReflectionTestUtils.setField(category, "categoryId", 3L);
			assertSame(owner, category.getOwnerUser());
			assertFalse(category.isSystem());
			assertFalse(category.isNameLocked());
			when(categories.findById(3L)).thenReturn(Optional.of(category));
			when(categories.findAllByOwnerUserIsNullOrOwnerUser_UserId(1L)).thenReturn(List.of(category));
			return category;
		});
		when(settings.save(any())).thenAnswer(call -> {
			CalendarCategorySetting setting = call.getArgument(0);
			when(settings.findByUser_UserIdAndCategory_CategoryId(1L, 3L)).thenReturn(Optional.of(setting));
			when(settings.findAllByUser_UserId(1L)).thenReturn(List.of(setting));
			return setting;
		});
		var created = service.createCategory(1L, new CalendarCategoryCreateRequest("스터디", "#123456"));
		assertTrue(created.code().startsWith("CUSTOM-"));
		assertFalse(created.fixed());
		var updated = service.updateCategory(1L, 3L, new CalendarCategoryUpdateRequest("독서", "#654321", false));
		assertEquals("독서", updated.label());
		assertFalse(updated.enabled());
		service.deleteCategory(1L, 3L);
		verify(settings).deleteByCategory_CategoryId(3L);
		verify(categories).delete(any(CalendarCategory.class));
	}

	@Test
	void bothSystemCategoriesRejectUpdateDeleteAndUserEventCreation() {
		for (String code : List.of("SCHOOL", "DORMITORY")) {
			var system = new CalendarCategory(code, code, null, true, true);
			when(categories.findById(2L)).thenReturn(Optional.of(system));
			assertForbidden(() -> service.updateCategory(1L, 2L,
					new CalendarCategoryUpdateRequest("변경", null, null)));
			assertForbidden(() -> service.deleteCategory(1L, 2L));
			LocalDate day = LocalDate.of(2026, 3, 3);
			assertForbidden(() -> service.createEvent(1L,
					new CalendarEventCreateRequest("일정", day, day, null, null, 2L, null)));
		}
		verify(events, never()).save(any());
		verify(categories, never()).delete(any());
	}

	@Test
	void anotherUsersCustomCategoryIsProtected() {
		var category = new CalendarCategory("CUSTOM-test", "스터디", owner, false, false);
		when(categories.findById(3L)).thenReturn(Optional.of(category));
		assertForbidden(() -> service.updateCategory(2L, 3L,
				new CalendarCategoryUpdateRequest("변경", null, null)));
		assertForbidden(() -> service.deleteCategory(2L, 3L));
	}

	private void assertForbidden(org.junit.jupiter.api.function.Executable action) {
		assertEquals(ErrorCode.FORBIDDEN, assertThrows(CustomException.class, action).getErrorCode());
	}
}
