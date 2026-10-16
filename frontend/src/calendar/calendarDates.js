export const CALENDAR_TIME_OPTIONS = Array.from({ length: 96 }, (_, index) => {
  const minutes = index * 15;
  return `${String(Math.floor(minutes / 60)).padStart(2, "0")}:${String(minutes % 60).padStart(2, "0")}`;
});

export function dateKey(date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

export function nextDate(value) {
  const [year, month, day] = value.split("-").map(Number);
  return dateKey(new Date(year, month - 1, day + 1));
}

export function defaultEventTimes(date, now = new Date()) {
  const startIndex = Math.floor((now.getHours() * 60 + now.getMinutes()) / 15) + 1;
  const endIndex = startIndex + 1;
  const startDayOffset = Math.floor(startIndex / CALENDAR_TIME_OPTIONS.length);
  const endDayOffset = Math.floor(endIndex / CALENDAR_TIME_OPTIONS.length);
  return {
    date: startDayOffset ? nextDate(date) : date,
    startTime: CALENDAR_TIME_OPTIONS[startIndex % CALENDAR_TIME_OPTIONS.length],
    endTime: CALENDAR_TIME_OPTIONS[endIndex % CALENDAR_TIME_OPTIONS.length],
    endDate: endDayOffset ? nextDate(date) : date,
  };
}

// The API accepts 00:00–23:59; 24:00 is midnight on the following date.
export function normalizeEventTimes(event) {
  const endDate = event.endDate || event.date;
  return {
    ...event,
    date: event.startTime === "24:00" ? nextDate(event.date) : event.date,
    startTime: event.startTime === "24:00" ? "00:00" : event.startTime,
    endDate: event.endTime === "24:00" ? nextDate(endDate) : endDate,
    endTime: event.endTime === "24:00" ? "00:00" : event.endTime,
  };
}

export function overlaps(event, from, to = from) {
  return event.date <= to && (event.endDate || event.date) >= from;
}

export function formatTimeLabel(time) {
  if (!time) return "";
  const [hours, minutes] = time.split(":").map(Number);
  const hour = hours % 24;
  const period = hour < 12 ? "AM" : "PM";
  const label = `${period} ${String(hour % 12 || 12).padStart(2, "0")}:${String(minutes).padStart(2, "0")}`;
  return hours === 24 ? `${label} (24:00)` : label;
}

export function eventTimeLabel(event) {
  if (!event.startTime && !event.endTime) return "종일";
  return `${event.startTime ? formatTimeLabel(event.startTime) : "시작 시간 미정"} – ${event.endTime ? formatTimeLabel(event.endTime) : "종료 시간 미정"}`;
}

export function calendarEventDates(event) {
  // Date-range placement for incomplete times; the known bound is shown by eventTimeLabel.
  // allDay here is a FullCalendar layout flag and does not change the stored API event.
  return event.allDay || !event.startTime || !event.endTime
    ? { start: event.date, end: nextDate(event.endDate || event.date), allDay: true }
    : { start: `${event.date}T${event.startTime}:00`,
      end: `${event.endDate || event.date}T${event.endTime}:00`, allDay: false };
}
