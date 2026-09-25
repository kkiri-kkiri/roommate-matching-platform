export function dateKey(date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

export function nextDate(value) {
  const [year, month, day] = value.split("-").map(Number);
  return dateKey(new Date(year, month - 1, day + 1));
}

export function overlaps(event, from, to = from) {
  return event.date <= to && (event.endDate || event.date) >= from;
}

export function eventTimeLabel(event) {
  return event.allDay ? "종일" : `${event.startTime} – ${event.endTime}`;
}

export function calendarEventDates(event) {
  return event.allDay
    ? { start: event.date, end: nextDate(event.endDate || event.date), allDay: true }
    : { start: `${event.date}T${event.startTime}:00`,
      end: `${event.endDate || event.date}T${event.endTime}:00`, allDay: false };
}
