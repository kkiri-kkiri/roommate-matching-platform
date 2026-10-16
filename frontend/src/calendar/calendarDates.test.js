import { calendarEventDates, eventTimeLabel } from "./calendarDates";
import { parseMarker } from "../../node_modules/@fullcalendar/core/internal.cjs";

const dates = { date: "2026-10-16", endDate: "2026-10-18" };
test.each([
  ["10:00", "", "AM 10:00 – 종료 시간 미정"],
  ["", "11:00", "시작 시간 미정 – AM 11:00"],
])("one-sided time %s / %s preserves dates without inventing a clock time", (startTime, endTime, label) => {
  const event = { ...dates, startTime, endTime, allDay: false };
  expect(calendarEventDates(event)).toEqual({ start: "2026-10-16", end: "2026-10-19", allDay: true });
  expect(parseMarker(calendarEventDates(event).start)).not.toBeNull();
  expect(parseMarker(calendarEventDates(event).end)).not.toBeNull();
  expect(eventTimeLabel(event)).toBe(label);
});
test("both missing times render an inclusive all-day range", () => {
  const event = { ...dates, startTime: "", endTime: "", allDay: true };
  expect(calendarEventDates(event)).toEqual({ start: "2026-10-16", end: "2026-10-19", allDay: true });
  expect(eventTimeLabel(event)).toBe("종일");
});
test("complete timed range retains both exact timestamps", () => {
  expect(calendarEventDates({ ...dates, startTime: "23:45", endTime: "00:00", allDay: false }))
    .toEqual({ start: "2026-10-16T23:45:00", end: "2026-10-18T00:00:00", allDay: false });
});
test("all-day year boundary keeps the exclusive FullCalendar end", () => {
  expect(calendarEventDates({ date: "2026-12-31", endDate: "2027-01-02", allDay: true }))
    .toEqual({ start: "2026-12-31", end: "2027-01-03", allDay: true });
});
