import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import FullCalendar from "@fullcalendar/react";
import dayGridPlugin from "@fullcalendar/daygrid";
import interactionPlugin from "@fullcalendar/interaction";
import koLocale from "@fullcalendar/core/locales/ko";
import {
  CalendarDays, CalendarPlus, Check, ChevronLeft, ChevronRight,
  Clock3, Pencil, Plus, Trash2, X,
} from "lucide-react";

import * as S from "./CalendarPageCss";
import { dateKey, useCalendarData } from "./CalendarDataContext";
import CategorySettingsForm from "./CategorySettingsForm";
import { CALENDAR_TIME_OPTIONS, calendarEventDates, defaultEventTimes, eventTimeLabel, formatTimeLabel, nextDate, normalizeEventTimes, overlaps } from "./calendarDates";

const CALENDAR_PLUGINS = [dayGridPlugin, interactionPlugin];
const CALENDAR_TIME_FORMAT = ({ date }) => formatTimeLabel(`${date.hour}:${date.minute}`);

function colorStyle(color) {
  const channels = [1, 3, 5].map((offset) => parseInt(color.slice(offset, offset + 2), 16));
  const background = `rgb(${channels.map((channel) => Math.round(channel * 0.1 + 255 * 0.9)).join(", ")})`;
  const text = `rgb(${channels.map((channel) => Math.round(channel * 0.6)).join(", ")})`;
  return { color, background, text };
}

function localDate(value) {
  const [year, month, day] = value.split("-").map(Number);
  return new Date(year, month - 1, day);
}

function formatDate(value, includeYear = true) {
  return localDate(value).toLocaleDateString("ko-KR", {
    ...(includeYear ? { year: "numeric" } : {}),
    month: "long", day: "numeric", weekday: "long",
  });
}

function monthTitle(value) {
  return localDate(`${value}-01`).toLocaleDateString("ko-KR", { year: "numeric", month: "long" });
}

function isValidCalendarDate(value) {
  return /^\d{4}-\d{2}-\d{2}$/.test(value) && value >= "1900-01-01" &&
    value <= "2100-12-31" && dateKey(localDate(value)) === value;
}

function validateDraft(draft, categoryIds) {
  const errors = {};
  if (!draft.title.trim()) errors.title = "일정 제목을 입력해 주세요.";
  else if (draft.title.trim().length > 80) errors.title = "제목은 80자 이내로 입력해 주세요.";
  if (!isValidCalendarDate(draft.date)) {
    errors.date = "1900년부터 2100년 사이의 올바른 시작일을 선택해 주세요.";
  }
  if (!isValidCalendarDate(draft.endDate)) {
    errors.endDate = "1900년부터 2100년 사이의 올바른 종료일을 선택해 주세요.";
  } else if (!errors.date && draft.endDate < draft.date) {
    errors.endDate = "종료일은 시작일과 같거나 늦어야 해요.";
  }
  if (!CALENDAR_TIME_OPTIONS.includes(draft.startTime)) errors.startTime = "시작 시간을 15분 단위로 선택해 주세요.";
  if (!CALENDAR_TIME_OPTIONS.includes(draft.endTime)) errors.endTime = "종료 시간을 15분 단위로 선택해 주세요.";
  if (!errors.date && !errors.endDate && !errors.startTime && !errors.endTime) {
    const normalized = normalizeEventTimes(draft);
    if (!isValidCalendarDate(normalized.date)) errors.startTime = "24:00은 다음 날 00:00이에요. 시작일을 앞당겨 주세요.";
    if (!isValidCalendarDate(normalized.endDate)) errors.endTime = "24:00은 다음 날 00:00이에요. 종료일을 앞당겨 주세요.";
    else if (`${normalized.endDate}T${normalized.endTime}` <= `${normalized.date}T${normalized.startTime}`) {
      errors.endTime = "종료 시간은 시작 시간보다 늦어야 해요.";
    }
  }
  if (!categoryIds.includes(draft.category)) errors.category = "일정 분류를 선택해 주세요.";
  if (draft.description.length > 2000) errors.description = "내용은 2,000자 이내로 입력해 주세요.";
  return errors;
}

function CalendarDialog({ title, description, onDismiss, children }) {
  const dialogRef = useRef(null);

  useEffect(() => {
    const dialog = dialogRef.current;
    dialog.showModal();
    return () => dialog.close();
  }, []);

  return (
    <S.Dialog
      ref={dialogRef}
      aria-labelledby="calendar-dialog-title"
      aria-describedby={description ? "calendar-dialog-description" : undefined}
      onCancel={(event) => { event.preventDefault(); onDismiss(); }}
      onClick={(event) => {
        if (event.target !== event.currentTarget) return;
        const bounds = event.currentTarget.getBoundingClientRect();
        if (event.clientX < bounds.left || event.clientX > bounds.right ||
            event.clientY < bounds.top || event.clientY > bounds.bottom) onDismiss();
      }}
    >
      <S.DialogHeader>
        <S.DialogTitle id="calendar-dialog-title">{title}</S.DialogTitle>
        <S.IconButton type="button" aria-label="닫기" onClick={onDismiss} autoFocus>
          <X size={20} aria-hidden="true" />
        </S.IconButton>
      </S.DialogHeader>
      {description && <S.DialogDescription id="calendar-dialog-description">{description}</S.DialogDescription>}
      {children}
    </S.Dialog>
  );
}

function CalendarPage() {
  const [initialDate] = useState(() => new Date());
  const [range, setRange] = useState({ from: undefined, to: undefined });
  const { events, categorySettings: allCategories, saveEvent: persistEvent, removeEvent, saveCategories, authenticated, loading, error, retry } = useCalendarData(range.from, range.to);
  const categorySettings = useMemo(() => allCategories.filter((item) => item.enabled !== false), [allCategories]);
  const [saving, setSaving] = useState(false);
  const savingRef = useRef(false);
  const categoryIds = categorySettings.map(({ id }) => id);
  const categoryLabels = Object.fromEntries(categorySettings.map(({ id, label }) => [id, label]));
  const [selectedDate, setSelectedDate] = useState(() => dateKey(initialDate));
  const [visibleMonth, setVisibleMonth] = useState(() => dateKey(initialDate).slice(0, 7));
  const visibleWeekCount = useMemo(() => {
    const firstDay = localDate(`${visibleMonth}-01`);
    const days = new Date(firstDay.getFullYear(), firstDay.getMonth() + 1, 0).getDate();
    return Math.ceil((firstDay.getDay() + days) / 7);
  }, [visibleMonth]);
  const [categories, setCategories] = useState(categoryIds);
  const previousCategoryIds = useRef(categoryIds);
  useEffect(() => {
    const nextIds = categorySettings.map((item) => item.id);
    const previous = previousCategoryIds.current;
    setCategories((current) => nextIds.filter((id) => current.includes(id) || !previous.includes(id)));
    previousCategoryIds.current = nextIds;
  }, [categorySettings]);
  const categoryStyles = useMemo(() => Object.fromEntries(
    categorySettings.map(({ id, color }) => [id, colorStyle(color)]),
  ), [categorySettings]);
  const categoryVariables = Object.fromEntries(categoryIds.flatMap((id) =>
    Object.entries(categoryStyles[id]).map(([name, value]) => [`--category-${id}-${name}`, value]),
  ));

  const [agendaScope, setAgendaScope] = useState("day");
  const [dialog, setDialog] = useState(null);
  const [draft, setDraft] = useState(null);
  const [errors, setErrors] = useState({});
  const calendarRef = useRef(null);
  const addButtonRef = useRef(null);
  const dialogTriggerRef = useRef(null);

  const filteredEvents = useMemo(() => events
    .filter((event) => categories.includes(event.category))
    .sort((first, second) => `${first.date}T${first.startTime}`.localeCompare(`${second.date}T${second.startTime}`)),
  [events, categories]);
  const monthEnd = dateKey(new Date(Number(visibleMonth.slice(0, 4)), Number(visibleMonth.slice(5)), 0));
  const monthEvents = filteredEvents.filter((event) => overlaps(event, `${visibleMonth}-01`, monthEnd));
  const dayEvents = filteredEvents.filter((event) => overlaps(event, selectedDate));
  const agendaEvents = agendaScope === "day" ? dayEvents : monthEvents;
  const eventDates = useMemo(() => {
    const dates = new Set();
    for (let day = range.from; day && day <= range.to; day = nextDate(day)) {
      if (filteredEvents.some((event) => overlaps(event, day))) dates.add(day);
    }
    return dates;
  }, [filteredEvents, range]);
  const activeEvent = dialog?.eventId ? events.find((event) => event.id === dialog.eventId) : null;
  const isFormOpen = dialog?.mode === "create" || dialog?.mode === "edit";
  const calendarEvents = useMemo(() => filteredEvents.map((event) => {
    const categoryStyle = categoryStyles[event.category] || colorStyle("#006a65");
    return {
      id: event.id, title: event.title,
      ...calendarEventDates(event),
      backgroundColor: categoryStyle.background,
      borderColor: categoryStyle.color,
      textColor: categoryStyle.text,
      extendedProps: { category: event.category, description: event.description },
    };
  }), [filteredEvents, categoryStyles]);

  const handleDatesSet = useCallback(({ view, start, end }) => {
    const lastDate = new Date(end);
    lastDate.setDate(lastDate.getDate() - 1);
    const from = dateKey(start);
    const to = dateKey(lastDate);
    setRange((current) => current.from === from && current.to === to ? current : { from, to });
    const firstDate = dateKey(view.currentStart);
    const nextMonth = firstDate.slice(0, 7);
    setVisibleMonth(nextMonth);
    setSelectedDate((current) => current.startsWith(nextMonth) ? current : firstDate);
  }, []);

  const selectDate = (value) => {
    calendarRef.current?.getApi().gotoDate(value);
    setSelectedDate(value);
    setAgendaScope("day");
  };

  const goToday = () => selectDate(dateKey(new Date()));

  const toggleCategory = (categoryId) => {
    setCategories((current) => current.includes(categoryId)
      ? current.filter((id) => id !== categoryId) : [...current, categoryId]);
  };

  const closeDialog = () => {
    if (savingRef.current) return;
    setDialog(null);
    setErrors({});
    requestAnimationFrame(() => {
      const trigger = dialogTriggerRef.current;
      if (trigger?.isConnected && trigger.matches("button, a, input, select, textarea, [tabindex]")) trigger.focus({ preventScroll: true });
      else {
        const selectedDay = calendarRef.current?.getApi().el.querySelector(".fc-day-selected button");
        (selectedDay || addButtonRef.current)?.focus({ preventScroll: true });
      }
    });
  };

  const openCreate = (date = selectedDate) => {
    if (!authenticated) { window.alert("로그인 후 일정을 등록할 수 있어요."); return; }
    dialogTriggerRef.current = document.activeElement;
    setDraft({ title: "", date, ...defaultEventTimes(date), category: "", description: "" });
    setErrors({});
    setDialog({ mode: "create" });
  };

  const openDetail = (eventId) => {
    dialogTriggerRef.current = document.activeElement;
    setErrors({});
    setDialog({ mode: "detail", eventId });
  };

  const openEdit = () => {
    if (savingRef.current || activeEvent.editable === false) return;
    setDraft(normalizeEventTimes({ ...activeEvent, endDate: activeEvent.endDate || activeEvent.date }));
    setErrors({});
    setDialog({ mode: "edit", eventId: activeEvent.id });
  };

  const openCategorySettings = () => {
    if (!authenticated) { window.alert("로그인 후 카테고리를 설정할 수 있어요."); return; }
    dialogTriggerRef.current = document.activeElement;
    setDialog({ mode: "categories" });
  };

  const updateDraft = (event) => {
    const { name, value } = event.target;
    setDraft((current) => ({
      ...current,
      [name]: value,
      ...(name === "date" && current.endDate < value ? { endDate: value } : {}),
    }));
    setErrors((current) => ({
      ...current,
      [name]: undefined,
      ...(name === "date" ? { endDate: undefined, endTime: undefined } : {}),
      ...(name === "endDate" || name === "startTime" ? { endTime: undefined } : {}),
    }));
  };

  const saveEvent = async (event) => {
    event.preventDefault();
    if (savingRef.current) return;
    const nextErrors = validateDraft(draft, categorySettings.filter((item) => !item.fixed).map((item) => item.id));
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) {
      event.currentTarget.elements.namedItem(Object.keys(nextErrors)[0])?.focus();
      return;
    }
    const isEditing = dialog.mode === "edit";
    savingRef.current = true; setSaving(true);
    try {
      const savedEvent = await persistEvent(draft, isEditing ? dialog.eventId : undefined);
      setCategories((current) => current.includes(savedEvent.category) ? current : [...current, savedEvent.category]);
      selectDate(savedEvent.date);
      savingRef.current = false;
      closeDialog();
      window.alert(isEditing ? "일정을 수정했어요." : "일정을 등록했어요.");
    } catch (error) { window.alert(error.message); }
    finally { savingRef.current = false; setSaving(false); }
  };

  const deleteEvent = async () => {
    if (activeEvent.editable === false || savingRef.current) return;
    savingRef.current = true; setSaving(true); setErrors({});
    try {
      await removeEvent(activeEvent.id);
      savingRef.current = false;
      closeDialog();
      window.alert("일정을 삭제했어요.");
    } catch (error) { window.alert(error.message); }
    finally { savingRef.current = false; setSaving(false); }
  };

  const fieldProps = (name) => ({
    id: `calendar-${name}`, name, value: draft[name], onChange: updateDraft,
    "aria-invalid": Boolean(errors[name]),
    "aria-describedby": errors[name] ? `calendar-${name}-error` : undefined,
  });

  const fieldError = (name) => errors[name]
    ? <S.FieldError id={`calendar-${name}-error`} role="alert">{errors[name]}</S.FieldError> : null;

  return (
    <S.Page id="campus-calendar" style={categoryVariables}>
      <S.Content>
        {error && <div role="alert">{error} <S.SecondaryButton type="button" onClick={retry}>다시 시도</S.SecondaryButton></div>}

        <S.Workspace>
          <S.CalendarCard aria-labelledby="calendar-month-title">
            <S.Toolbar>
              <S.MonthNavigation>
                <S.IconButton type="button" aria-label="이전 달" onClick={() => calendarRef.current?.getApi().prev()}>
                  <ChevronLeft size={20} aria-hidden="true" />
                </S.IconButton>
                <S.MonthTitle id="calendar-month-title" aria-live="polite">{monthTitle(visibleMonth)}</S.MonthTitle>
                <S.IconButton type="button" aria-label="다음 달" onClick={() => calendarRef.current?.getApi().next()}>
                  <ChevronRight size={20} aria-hidden="true" />
                </S.IconButton>
              </S.MonthNavigation>
              <S.SecondaryButton type="button" onClick={goToday}>오늘</S.SecondaryButton>
            </S.Toolbar>

            <S.Filters aria-label="일정 분류 필터 및 색상 설정">
              <S.FilterButton type="button" $active={categories.length === categoryIds.length}
                aria-pressed={categories.length === categoryIds.length} onClick={() => setCategories(categoryIds)}>
                전체
              </S.FilterButton>
              {categorySettings.map(({ id, label }) => (
                <S.FilterButton key={id} type="button" $category={id} $active={categories.includes(id)}
                  aria-pressed={categories.includes(id)} onClick={() => toggleCategory(id)}>
                  <S.ColorDot $category={id} aria-hidden="true" />{label}
                  {categories.includes(id) && <Check size={12} aria-hidden="true" />}
                </S.FilterButton>
              ))}
              <S.IconButton type="button" aria-label="카테고리 설정" title="카테고리 설정" onClick={openCategorySettings}>
                <Plus size={20} aria-hidden="true" />
              </S.IconButton>
            </S.Filters>

            <S.CalendarSurface aria-busy={loading} $weekCount={visibleWeekCount} onDoubleClick={(event) => {
              if (event.target.closest(".fc-event, .fc-daygrid-more-link")) return;
              const date = event.target.closest(".fc-daygrid-day[data-date]")?.dataset.date;
              if (!date) return;
              selectDate(date);
              openCreate(date);
            }}>
              <FullCalendar
                ref={calendarRef}
                plugins={CALENDAR_PLUGINS}
                locale={koLocale}
                initialView="dayGridMonth"
                initialDate={dateKey(initialDate)}
                headerToolbar={false}
                height="100%"
                expandRows
                firstDay={0}
                fixedWeekCount={false}
                showNonCurrentDates
                navLinks={false}
                editable={false}
                events={calendarEvents}
                eventDisplay="block"
                eventOrder="start,title"
                eventInteractive={false}
                dayMaxEvents={2}
                displayEventTime
                eventTimeFormat={CALENDAR_TIME_FORMAT}
                dayHeaderFormat={{ weekday: "short" }}
                datesSet={handleDatesSet}
                dateClick={({ dateStr }) => selectDate(dateStr)}
                eventContent={({ event, timeText }) => (
                  <S.CalendarEvent title={`${categoryLabels[event.extendedProps.category]} · ${event.title} · ${timeText}`}>
                    <span>{timeText}</span> {event.title}
                  </S.CalendarEvent>
                )}
                dayCellClassNames={({ date }) => [
                  ...(dateKey(date) === selectedDate ? ["fc-day-selected"] : []),
                  ...(eventDates.has(dateKey(date)) ? ["fc-day-has-events"] : []),
                ]}
                dayCellContent={({ date, isToday }) => {
                  const value = dateKey(date);
                  return (
                    <S.DayNumber type="button" $today={isToday}
                      aria-pressed={value === selectedDate} aria-current={isToday ? "date" : undefined}
                      aria-label={`${formatDate(value)}${isToday ? ", 오늘" : ""}${eventDates.has(value) ? ", 일정 있음" : ", 일정 없음"}`}
                      onClick={(event) => { event.stopPropagation(); selectDate(value); }}>
                      {date.getDate()}
                    </S.DayNumber>
                  );
                }}
                moreLinkText={(count) => `+${count}개`}
                moreLinkClick="popover"
              />
            </S.CalendarSurface>
          </S.CalendarCard>
          <S.Sidebar aria-labelledby="calendar-agenda-title" aria-busy={loading}>
            <S.Tabs aria-label="일정 조회 범위">
              <S.TabButton type="button" $active={agendaScope === "day"} aria-pressed={agendaScope === "day"} onClick={() => setAgendaScope("day")}>선택한 날짜</S.TabButton>
              <S.TabButton type="button" $active={agendaScope === "month"} aria-pressed={agendaScope === "month"} onClick={() => setAgendaScope("month")}>이번 달 전체</S.TabButton>
            </S.Tabs>
            <S.AgendaHeader>
              <div>
                <S.Eyebrow>{agendaScope === "day" ? "DAILY SCHEDULE" : "MONTHLY SCHEDULE"}</S.Eyebrow>
                <S.AgendaTitle id="calendar-agenda-title">
                  {agendaScope === "day" ? formatDate(selectedDate, false) : monthTitle(visibleMonth)}
                </S.AgendaTitle>
              </div>
              <S.CountBadge aria-label={`${agendaEvents.length}개 일정`}>{agendaEvents.length}</S.CountBadge>
            </S.AgendaHeader>

            {error && <div role="alert">{error} <S.SecondaryButton type="button" onClick={retry}>다시 시도</S.SecondaryButton></div>}
            {agendaEvents.length > 0 ? (
              <S.AgendaList>
                {agendaEvents.map((event) => (
                  <S.AgendaItem key={event.id}>
                    <S.AgendaButton type="button" onClick={() => openDetail(event.id)}
                      aria-label={`${event.title}, ${formatDate(event.date, false)}, ${eventTimeLabel(event)}, ${categoryLabels[event.category]}, 상세 보기`}>
                      <S.AgendaMeta>
                        <S.CategoryBadge $category={event.category}><S.ColorDot $category={event.category} aria-hidden="true" />{categoryLabels[event.category]}</S.CategoryBadge>
                        <ChevronRight size={16} aria-hidden="true" />
                      </S.AgendaMeta>
                      <S.EventTitle>{event.title}</S.EventTitle>
                      {agendaScope === "month" && <S.EventTime><CalendarDays size={14} aria-hidden="true" />{formatDate(event.date, false)}</S.EventTime>}
                      <S.EventTime><Clock3 size={14} aria-hidden="true" />{eventTimeLabel(event)}</S.EventTime>
                    </S.AgendaButton>
                  </S.AgendaItem>
                ))}
              </S.AgendaList>
            ) : !loading && !error && (
              <S.EmptyState>
                <S.EmptyIcon><CalendarDays size={28} aria-hidden="true" /></S.EmptyIcon>
                <strong>{categories.length === 0 ? "선택된 분류가 없어요" : "아직 일정이 없어요"}</strong>
                <p>{categories.length === 0 ? "원하는 일정 분류를 선택해 주세요." : "여유로운 하루에 새로운 계획을 더해 보세요."}</p>
                {categories.length === 0 && <S.GhostButton type="button" onClick={() => setCategories(categoryIds)}>전체 분류 보기</S.GhostButton>}
              </S.EmptyState>
            )}
            <S.SecondaryButton ref={addButtonRef} type="button" aria-haspopup="dialog" onClick={() => openCreate()}>
              <CalendarPlus size={18} aria-hidden="true" /> 일정 등록
            </S.SecondaryButton>
          </S.Sidebar>
        </S.Workspace>

        {dialog?.mode === "categories" && (
          <CalendarDialog key="categories" title="카테고리 설정" onDismiss={closeDialog}
            description="학교 일정과 기숙사 일정은 고정 카테고리예요. 색상은 자유롭게 변경할 수 있어요.">
            <CategorySettingsForm initialCategories={categorySettings} events={events}
              onSave={saveCategories} onDismiss={closeDialog} savingRef={savingRef} />
          </CalendarDialog>
        )}

        {isFormOpen && draft && (
          <CalendarDialog key={dialog.mode} title={dialog.mode === "edit" ? "일정 수정" : "새 일정 등록"}
            onDismiss={closeDialog}>
            <S.Form onSubmit={saveEvent} noValidate>
              <fieldset disabled={saving} style={{ border: 0, padding: 0, margin: 0, minWidth: 0 }}>
              {fieldError("submit")}
              <S.FormGrid>
                <S.Field $wide>
                  <label htmlFor="calendar-title">일정 제목 *</label>
                  <S.Input {...fieldProps("title")} required maxLength={80} placeholder="어떤 일정인가요?" />
                  {fieldError("title")}
                </S.Field>
                <S.Field>
                  <label htmlFor="calendar-date">시작일 *</label>
                  <S.Input {...fieldProps("date")} type="date" required min="1900-01-01" max="2100-12-31" />
                  {fieldError("date")}
                </S.Field>
                <S.Field>
                  <label htmlFor="calendar-endDate">종료일 *</label>
                  <S.Input {...fieldProps("endDate")} type="date" required min={draft.date || "1900-01-01"} max="2100-12-31" />
                  {fieldError("endDate")}
                </S.Field>
                <S.Field>
                  <label htmlFor="calendar-startTime">시작 시간 *</label>
                  <S.Select {...fieldProps("startTime")} required>
                    <option value="">시작 시간을 선택해 주세요</option>
                    {CALENDAR_TIME_OPTIONS.map((time) => <option key={time} value={time}>{formatTimeLabel(time)}</option>)}
                  </S.Select>
                  {fieldError("startTime")}
                </S.Field>
                <S.Field>
                  <label htmlFor="calendar-endTime">종료 시간 *</label>
                  <S.Select {...fieldProps("endTime")} required>
                    <option value="">종료 시간을 선택해 주세요</option>
                    {CALENDAR_TIME_OPTIONS.map((time) => <option key={time} value={time}>{formatTimeLabel(time)}</option>)}
                  </S.Select>
                  {fieldError("endTime")}
                </S.Field>
                <S.Field $wide>
                  <label htmlFor="calendar-category">일정 분류 *</label>
                  <S.Select {...fieldProps("category")} required>
                    <option value="">분류를 선택해 주세요</option>
                    {categorySettings.filter((item) => !item.fixed).map(({ id, label }) => <option key={id} value={id}>{label}</option>)}
                  </S.Select>
                  {fieldError("category")}
                </S.Field>
                <S.Field $wide>
                  <label htmlFor="calendar-description">일정 내용 <span>(선택)</span></label>
                  <S.Textarea {...fieldProps("description")} rows={4} maxLength={2000} placeholder="장소, 준비물 등 기억할 내용을 적어 주세요." />
                  {fieldError("description")}
                  <S.FormHint>{draft.description.length.toLocaleString()} / 2,000자</S.FormHint>
                </S.Field>
              </S.FormGrid>
              <S.DialogActions>
                <S.GhostButton type="button" onClick={closeDialog}>취소</S.GhostButton>
                <S.PrimaryButton type="submit">{dialog.mode === "edit" ? "변경 내용 저장" : "일정 등록"}</S.PrimaryButton>
              </S.DialogActions>
              </fieldset>
            </S.Form>
          </CalendarDialog>
        )}

        {dialog?.mode === "detail" && activeEvent && (
          <CalendarDialog key="detail" title="일정 상세" onDismiss={closeDialog}>
            {fieldError("submit")}
            <S.DialogBody>
              <S.CategoryBadge $category={activeEvent.category}><S.ColorDot $category={activeEvent.category} aria-hidden="true" />{categoryLabels[activeEvent.category]}</S.CategoryBadge>
              <S.DetailTitle>{activeEvent.title}</S.DetailTitle>
              <S.DetailList>
                <div><dt>날짜</dt><dd>{formatDate(activeEvent.date)}{activeEvent.endDate && activeEvent.endDate !== activeEvent.date && ` ~ ${formatDate(activeEvent.endDate)}`}</dd></div>
                <div><dt>시간</dt><dd>{eventTimeLabel(activeEvent)}</dd></div>
              </S.DetailList>
              <S.DetailDescription><h3>일정 내용</h3><p>{activeEvent.description || "등록된 내용이 없어요."}</p></S.DetailDescription>
              {activeEvent.editable !== false && <S.DialogActions>
                <S.DangerButton type="button" disabled={saving} onClick={deleteEvent}><Trash2 size={16} aria-hidden="true" /> 삭제</S.DangerButton>
                <S.PrimaryButton type="button" onClick={openEdit}><Pencil size={16} aria-hidden="true" /> 일정 수정</S.PrimaryButton>
              </S.DialogActions>}
            </S.DialogBody>
          </CalendarDialog>
        )}

      </S.Content>
    </S.Page>
  );
}

export default CalendarPage;
