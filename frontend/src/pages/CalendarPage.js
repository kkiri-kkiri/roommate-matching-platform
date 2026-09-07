import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import FullCalendar from "@fullcalendar/react";
import dayGridPlugin from "@fullcalendar/daygrid";
import interactionPlugin from "@fullcalendar/interaction";
import koLocale from "@fullcalendar/core/locales/ko";
import {
  CalendarDays, CalendarPlus, Check, ChevronLeft, ChevronRight,
  Clock3, Filter, Pencil, Plus, Trash2, X,
} from "lucide-react";

import * as S from "./CalendarPageCss";

// 분류별 색상은 CalendarPageCss의 CATEGORY_STYLES 한곳에서 변경합니다.
const CATEGORIES = [
  { id: "school", label: "학교 일정" },
  { id: "dormitory", label: "기숙사 일정" },
  { id: "roommate", label: "룸메찾기 일정" },
  { id: "personal", label: "개인 일정" },
];
const CATEGORY_IDS = CATEGORIES.map(({ id }) => id);
const CATEGORY_LABELS = Object.fromEntries(CATEGORIES.map(({ id, label }) => [id, label]));
const CALENDAR_PLUGINS = [dayGridPlugin, interactionPlugin];

function dateKey(date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
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

function createSampleEvents(today) {
  const sampleDate = (day, offset = 0) => dateKey(new Date(today.getFullYear(), today.getMonth() + offset, day));
  return [
    { id: "sample-1", title: "학기 일정 확인", date: sampleDate(2), startTime: "09:00", endTime: "10:00", category: "school", description: "이번 학기 수업과 학사 일정을 확인해요." },
    { id: "sample-2", title: "생활관 정기 점검", date: sampleDate(8), startTime: "18:00", endTime: "19:00", category: "dormitory", description: "공용 공간과 생활관 시설을 함께 점검해요.\n점검 전에 개인 물품을 정리해 주세요." },
    { id: "sample-3", title: "룸메이트와 첫 만남", date: dateKey(today), startTime: "12:00", endTime: "13:00", category: "roommate", description: "학생회관 1층에서 만나 함께 점심을 먹어요.\n서로의 생활 패턴과 공용 물품에 대해 이야기해요." },
    { id: "sample-4", title: "도서관에서 과제하기", date: dateKey(today), startTime: "15:00", endTime: "17:00", category: "personal", description: "중앙도서관에서 이번 주 과제를 정리해요." },
    { id: "sample-5", title: "학과 진로 특강", date: sampleDate(16), startTime: "14:00", endTime: "16:00", category: "school", description: "전공과 관련된 진로를 알아보는 시간이에요. 필기 도구를 준비해요." },
    { id: "sample-6", title: "룸메이트 생활 규칙 정하기", date: sampleDate(21), startTime: "19:00", endTime: "20:00", category: "roommate", description: "청소 순서와 취침 시간, 공용 물품 사용 규칙을 함께 정해요." },
    { id: "sample-7", title: "생활관 소방 대피 훈련", date: sampleDate(24), startTime: "18:30", endTime: "19:00", category: "dormitory", description: "생활관 앞 지정 장소에 모여 대피 경로를 확인해요." },
    { id: "sample-8", title: "한 달 생활비 정리", date: sampleDate(27), startTime: "20:00", endTime: "20:30", category: "personal", description: "이번 달 지출을 돌아보고 다음 달 예산을 정해요." },
    { id: "sample-9", title: "생활관 입사 준비", date: sampleDate(26, -1), startTime: "10:00", endTime: "11:00", category: "dormitory", description: "입사에 필요한 물품과 준비 서류를 확인해요." },
    { id: "sample-10", title: "다음 달 학습 계획", date: sampleDate(3, 1), startTime: "16:00", endTime: "17:00", category: "personal", description: "수업별 학습 목표를 세우고 달력에 정리해요." },
  ];
}

function validateDraft(draft) {
  const errors = {};
  if (!draft.title.trim()) errors.title = "일정 제목을 입력해 주세요.";
  else if (draft.title.trim().length > 80) errors.title = "제목은 80자 이내로 입력해 주세요.";
  if (!/^\d{4}-\d{2}-\d{2}$/.test(draft.date) || draft.date < "1900-01-01" ||
      draft.date > "2100-12-31" || dateKey(localDate(draft.date)) !== draft.date) {
    errors.date = "1900년부터 2100년 사이의 올바른 날짜를 선택해 주세요.";
  }
  const timePattern = /^([01]\d|2[0-3]):[0-5]\d$/;
  if (!timePattern.test(draft.startTime)) errors.startTime = "시작 시간을 선택해 주세요.";
  if (!timePattern.test(draft.endTime)) errors.endTime = "종료 시간을 선택해 주세요.";
  else if (!errors.startTime && draft.endTime <= draft.startTime) errors.endTime = "종료 시간은 시작 시간보다 늦어야 해요.";
  if (!CATEGORY_IDS.includes(draft.category)) errors.category = "일정 분류를 선택해 주세요.";
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
  const [events, setEvents] = useState(() => createSampleEvents(initialDate));
  const [selectedDate, setSelectedDate] = useState(() => dateKey(initialDate));
  const [visibleMonth, setVisibleMonth] = useState(() => dateKey(initialDate).slice(0, 7));
  const [categories, setCategories] = useState(CATEGORY_IDS);
  const [agendaScope, setAgendaScope] = useState("day");
  const [dialog, setDialog] = useState(null);
  const [draft, setDraft] = useState(null);
  const [errors, setErrors] = useState({});
  const [status, setStatus] = useState("");
  const calendarRef = useRef(null);
  const addButtonRef = useRef(null);
  const dialogTriggerRef = useRef(null);
  const eventSequence = useRef(0);

  const filteredEvents = useMemo(() => events
    .filter((event) => categories.includes(event.category))
    .sort((first, second) => `${first.date}T${first.startTime}`.localeCompare(`${second.date}T${second.startTime}`)),
  [events, categories]);
  const monthEvents = filteredEvents.filter((event) => event.date.startsWith(visibleMonth));
  const dayEvents = filteredEvents.filter((event) => event.date === selectedDate);
  const agendaEvents = agendaScope === "day" ? dayEvents : monthEvents;
  const eventDates = useMemo(() => new Set(filteredEvents.map((event) => event.date)), [filteredEvents]);
  const totalMonthEvents = events.filter((event) => event.date.startsWith(visibleMonth)).length;
  const activeEvent = dialog?.eventId ? events.find((event) => event.id === dialog.eventId) : null;
  const isFormOpen = dialog?.mode === "create" || dialog?.mode === "edit";
  const calendarEvents = useMemo(() => filteredEvents.map((event) => {
    const categoryStyle = S.CATEGORY_STYLES[event.category];
    return {
      id: event.id, title: event.title,
      start: `${event.date}T${event.startTime}:00`, end: `${event.date}T${event.endTime}:00`,
      allDay: false,
      backgroundColor: categoryStyle.background,
      borderColor: categoryStyle.color,
      textColor: categoryStyle.color,
      extendedProps: { category: event.category, description: event.description },
    };
  }), [filteredEvents]);

  const handleDatesSet = useCallback(({ view }) => {
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
    setDialog(null);
    setErrors({});
    requestAnimationFrame(() => {
      const trigger = dialogTriggerRef.current;
      if (trigger?.isConnected) trigger.focus({ preventScroll: true });
      else addButtonRef.current?.focus({ preventScroll: true });
    });
  };

  const openCreate = (date = selectedDate) => {
    dialogTriggerRef.current = document.activeElement;
    setDraft({ title: "", date, startTime: "", endTime: "", category: "", description: "" });
    setErrors({});
    setStatus("");
    setDialog({ mode: "create" });
  };

  const openDetail = (eventId) => {
    dialogTriggerRef.current = document.activeElement;
    setErrors({});
    setDialog({ mode: "detail", eventId });
  };

  const openEdit = () => {
    setDraft({ ...activeEvent });
    setErrors({});
    setDialog({ mode: "edit", eventId: activeEvent.id });
  };

  const updateDraft = (event) => {
    const { name, value } = event.target;
    setDraft((current) => ({ ...current, [name]: value }));
    setErrors((current) => ({ ...current, [name]: undefined, ...(name === "startTime" ? { endTime: undefined } : {}) }));
  };

  const saveEvent = (event) => {
    event.preventDefault();
    const nextErrors = validateDraft(draft);
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) {
      event.currentTarget.elements.namedItem(Object.keys(nextErrors)[0])?.focus();
      return;
    }
    const isEditing = dialog.mode === "edit";
    const savedEvent = {
      ...draft, title: draft.title.trim(), description: draft.description.trim(),
      id: isEditing ? dialog.eventId : `local-${Date.now()}-${eventSequence.current++}`,
    };
    setEvents((current) => isEditing
      ? current.map((item) => item.id === savedEvent.id ? savedEvent : item)
      : [...current, savedEvent]);
    // 숨겨진 분류로 저장해도 등록한 일정을 바로 확인할 수 있게 합니다.
    setCategories((current) => current.includes(savedEvent.category) ? current : [...current, savedEvent.category]);
    selectDate(savedEvent.date);
    closeDialog();
    setStatus(`“${savedEvent.title}” 일정을 ${isEditing ? "수정" : "등록"}했어요.`);
  };

  const deleteEvent = () => {
    const deletedTitle = activeEvent.title;
    setEvents((current) => current.filter((event) => event.id !== activeEvent.id));
    closeDialog();
    setStatus(`“${deletedTitle}” 일정을 삭제했어요.`);
  };

  const fieldProps = (name) => ({
    id: `calendar-${name}`, name, value: draft[name], onChange: updateDraft,
    "aria-invalid": Boolean(errors[name]),
    "aria-describedby": errors[name] ? `calendar-${name}-error` : undefined,
  });

  const fieldError = (name) => errors[name]
    ? <S.FieldError id={`calendar-${name}-error`} role="alert">{errors[name]}</S.FieldError> : null;

  return (
    <S.Page id="campus-calendar">
      <S.Content>
        <S.Breadcrumb aria-label="현재 위치">
          <S.TextLink to="/">홈</S.TextLink>
          <ChevronRight size={14} aria-hidden="true" />
          <span aria-current="page">캘린더</span>
        </S.Breadcrumb>

        <S.Intro>
          <div>
            <S.Eyebrow>PLAN YOUR CAMPUS LIFE</S.Eyebrow>
            <S.Title>우리의 일상을 한눈에</S.Title>
            <S.Description>학교부터 룸메이트와의 약속까지, 소중한 일정을 함께 챙겨요.</S.Description>
          </div>
          <S.PrimaryButton ref={addButtonRef} type="button" onClick={() => openCreate()}>
            <Plus size={18} aria-hidden="true" /> 일정 등록
          </S.PrimaryButton>
        </S.Intro>

        <S.Status role="status" aria-live="polite" aria-atomic="true">{status}</S.Status>

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
            <S.MonthSummary>이번 달 일정 <strong>{monthEvents.length}</strong>개 <span>· 전체 {totalMonthEvents}개</span></S.MonthSummary>

            <S.Filters>
              <legend><Filter size={14} aria-hidden="true" /> 일정 분류 <span>여러 분류를 함께 선택할 수 있어요</span></legend>
              <S.FilterButton type="button" $active={categories.length === CATEGORY_IDS.length}
                aria-pressed={categories.length === CATEGORY_IDS.length} onClick={() => setCategories(CATEGORY_IDS)}>
                전체
              </S.FilterButton>
              {CATEGORIES.map(({ id, label }) => (
                <S.FilterButton key={id} type="button" $category={id} $active={categories.includes(id)}
                  aria-pressed={categories.includes(id)} onClick={() => toggleCategory(id)}>
                  <S.ColorDot $category={id} aria-hidden="true" />{label}
                  {categories.includes(id) && <Check size={12} aria-hidden="true" />}
                </S.FilterButton>
              ))}
            </S.Filters>

            <S.CalendarSurface>
              <FullCalendar
                ref={calendarRef}
                plugins={CALENDAR_PLUGINS}
                locale={koLocale}
                initialView="dayGridMonth"
                initialDate={dateKey(initialDate)}
                headerToolbar={false}
                height="auto"
                firstDay={0}
                fixedWeekCount={false}
                showNonCurrentDates
                navLinks={false}
                editable={false}
                events={calendarEvents}
                eventDisplay="block"
                eventOrder="start,title"
                eventInteractive
                dayMaxEvents={2}
                displayEventTime
                eventTimeFormat={{ hour: "2-digit", minute: "2-digit", hour12: false }}
                dayHeaderFormat={{ weekday: "short" }}
                datesSet={handleDatesSet}
                dateClick={({ dateStr }) => selectDate(dateStr)}
                eventClick={({ event, jsEvent }) => { jsEvent.stopPropagation(); openDetail(event.id); }}
                eventContent={({ event, timeText }) => (
                  <S.CalendarEvent title={`${CATEGORY_LABELS[event.extendedProps.category]} · ${event.title} · ${timeText}`}>
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
                    <S.DayNumber type="button" $selected={value === selectedDate} $today={isToday}
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
            <S.CalendarHint><CalendarDays size={16} aria-hidden="true" /> 날짜를 선택하면 그날의 일정을 확인할 수 있어요.</S.CalendarHint>
            {monthEvents.length === 0 && (
              <S.EmptyState role="status">
                <strong>{categories.length === 0 ? "조회할 일정 분류를 선택해 주세요" : "이번 달에 표시할 일정이 없어요"}</strong>
                <p>{categories.length === 0 ? "위에서 원하는 분류를 하나 이상 선택해 주세요." : "다른 분류를 선택하거나 새로운 일정을 등록해 보세요."}</p>
              </S.EmptyState>
            )}
          </S.CalendarCard>

          <S.Sidebar aria-labelledby="calendar-agenda-title">
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

            {agendaEvents.length > 0 ? (
              <S.AgendaList>
                {agendaEvents.map((event) => (
                  <S.AgendaItem key={event.id}>
                    <S.AgendaButton type="button" onClick={() => openDetail(event.id)}
                      aria-label={`${event.title}, ${formatDate(event.date, false)}, ${event.startTime}부터 ${event.endTime}, ${CATEGORY_LABELS[event.category]}, 상세 보기`}>
                      <S.AgendaMeta>
                        <S.CategoryBadge $category={event.category}><S.ColorDot $category={event.category} aria-hidden="true" />{CATEGORY_LABELS[event.category]}</S.CategoryBadge>
                        <ChevronRight size={16} aria-hidden="true" />
                      </S.AgendaMeta>
                      <S.EventTitle>{event.title}</S.EventTitle>
                      {agendaScope === "month" && <S.EventTime><CalendarDays size={14} aria-hidden="true" />{formatDate(event.date, false)}</S.EventTime>}
                      <S.EventTime><Clock3 size={14} aria-hidden="true" />{event.startTime} – {event.endTime}</S.EventTime>
                    </S.AgendaButton>
                  </S.AgendaItem>
                ))}
              </S.AgendaList>
            ) : (
              <S.EmptyState>
                <S.EmptyIcon><CalendarDays size={28} aria-hidden="true" /></S.EmptyIcon>
                <strong>{categories.length === 0 ? "선택된 분류가 없어요" : "아직 일정이 없어요"}</strong>
                <p>{categories.length === 0 ? "원하는 일정 분류를 선택해 주세요." : "여유로운 하루에 새로운 계획을 더해 보세요."}</p>
                {categories.length === 0 && <S.GhostButton type="button" onClick={() => setCategories(CATEGORY_IDS)}>전체 분류 보기</S.GhostButton>}
              </S.EmptyState>
            )}
            <S.SecondaryButton type="button" onClick={() => openCreate()}>
              <CalendarPlus size={18} aria-hidden="true" /> 선택한 날짜에 일정 등록
            </S.SecondaryButton>
            <S.FormHint>예시 일정으로 구성된 화면이에요. 변경한 일정은 새로고침하면 초기화돼요.</S.FormHint>
          </S.Sidebar>
        </S.Workspace>

        {isFormOpen && draft && (
          <CalendarDialog key={dialog.mode} title={dialog.mode === "edit" ? "일정 수정" : "새 일정 등록"}
            description="기억하고 싶은 일정을 차근차근 채워 주세요." onDismiss={closeDialog}>
            <S.Form onSubmit={saveEvent} noValidate>
              <S.FormHint>* 표시는 필수 입력 항목이에요.</S.FormHint>
              <S.FormGrid>
                <S.Field $wide>
                  <label htmlFor="calendar-title">일정 제목 *</label>
                  <S.Input {...fieldProps("title")} required maxLength={80} placeholder="어떤 일정인가요?" />
                  {fieldError("title")}
                </S.Field>
                <S.Field $wide>
                  <label htmlFor="calendar-date">날짜 *</label>
                  <S.Input {...fieldProps("date")} type="date" required min="1900-01-01" max="2100-12-31" />
                  {fieldError("date")}
                </S.Field>
                <S.Field>
                  <label htmlFor="calendar-startTime">시작 시간 *</label>
                  <S.Input {...fieldProps("startTime")} type="time" required />
                  {fieldError("startTime")}
                </S.Field>
                <S.Field>
                  <label htmlFor="calendar-endTime">종료 시간 *</label>
                  <S.Input {...fieldProps("endTime")} type="time" required />
                  {fieldError("endTime")}
                </S.Field>
                <S.Field $wide>
                  <label htmlFor="calendar-category">일정 분류 *</label>
                  <S.Select {...fieldProps("category")} required>
                    <option value="">분류를 선택해 주세요</option>
                    {CATEGORIES.map(({ id, label }) => <option key={id} value={id}>{label}</option>)}
                  </S.Select>
                  {fieldError("category")}
                  {draft.category && <S.CategoryPreview>
                    <S.CategoryBadge $category={draft.category}><S.ColorDot $category={draft.category} aria-hidden="true" />{CATEGORY_LABELS[draft.category]}</S.CategoryBadge>
                    <span>이 분류의 색상으로 표시돼요.</span>
                  </S.CategoryPreview>}
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
            </S.Form>
          </CalendarDialog>
        )}

        {dialog?.mode === "detail" && activeEvent && (
          <CalendarDialog key="detail" title="일정 상세" onDismiss={closeDialog}>
            <S.DialogBody>
              <S.CategoryBadge $category={activeEvent.category}><S.ColorDot $category={activeEvent.category} aria-hidden="true" />{CATEGORY_LABELS[activeEvent.category]}</S.CategoryBadge>
              <S.DetailTitle>{activeEvent.title}</S.DetailTitle>
              <S.DetailList>
                <div><dt>날짜</dt><dd>{formatDate(activeEvent.date)}</dd></div>
                <div><dt>시작 시간</dt><dd>{activeEvent.startTime}</dd></div>
                <div><dt>종료 시간</dt><dd>{activeEvent.endTime}</dd></div>
              </S.DetailList>
              <S.DetailDescription><h3>일정 내용</h3><p>{activeEvent.description || "등록된 내용이 없어요."}</p></S.DetailDescription>
              <S.DialogActions>
                <S.DangerButton type="button" onClick={() => setDialog({ mode: "delete", eventId: activeEvent.id })}><Trash2 size={16} aria-hidden="true" /> 삭제</S.DangerButton>
                <S.PrimaryButton type="button" onClick={openEdit}><Pencil size={16} aria-hidden="true" /> 일정 수정</S.PrimaryButton>
              </S.DialogActions>
            </S.DialogBody>
          </CalendarDialog>
        )}

        {dialog?.mode === "delete" && activeEvent && (
          <CalendarDialog key="delete" title="일정을 삭제할까요?" description="삭제한 일정은 되돌릴 수 없어요." onDismiss={closeDialog}>
            <S.DialogBody>
              <S.DeleteNotice><strong>{activeEvent.title}</strong><span>{formatDate(activeEvent.date)} · {activeEvent.startTime} – {activeEvent.endTime}</span></S.DeleteNotice>
              <S.DialogActions>
                <S.GhostButton type="button" onClick={() => setDialog({ mode: "detail", eventId: activeEvent.id })}>취소</S.GhostButton>
                <S.DangerButton type="button" onClick={deleteEvent}><Trash2 size={16} aria-hidden="true" /> 일정 삭제</S.DangerButton>
              </S.DialogActions>
            </S.DialogBody>
          </CalendarDialog>
        )}
      </S.Content>
    </S.Page>
  );
}

export default CalendarPage;
