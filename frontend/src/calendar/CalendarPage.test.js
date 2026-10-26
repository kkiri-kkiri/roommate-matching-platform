import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import "@testing-library/jest-dom";
import CalendarPage from "./CalendarPage";
import { CalendarDataProvider } from "./CalendarDataContext";
import * as api from "../api/calendarApi";
jest.mock("../api/calendarApi");
jest.mock("@fullcalendar/daygrid", () => ({}));
jest.mock("@fullcalendar/interaction", () => ({}));
jest.mock("@fullcalendar/core/locales/ko", () => ({}));
jest.mock("@fullcalendar/react", () => {
  const React = require("react");
  return React.forwardRef(function Calendar(props, ref) {
    React.useImperativeHandle(ref, () => ({ getApi: () => ({ gotoDate: () => {}, el: globalThis.document.createElement("div") }) }));
    React.useEffect(() => {
      props.datesSet({ view: { currentStart: new Date(2026, 9, 1) }, start: new Date(2026, 9, 1), end: new Date(2026, 10, 1) });
    }, [props.datesSet]);
    return <div data-testid="calendar">{props.events.map(event => <div key={event.id}>
      {props.eventContent({ event, timeText: "" })}
    </div>)}</div>;
  });
});
beforeEach(() => {
  jest.resetAllMocks();
  HTMLDialogElement.prototype.showModal = function () { this.open = true; };
  HTMLDialogElement.prototype.close = function () { this.open = false; };
  api.getCalendarCategories.mockResolvedValue([
    { id: "school", serverId: 1, label: "학교", fixed: true, enabled: true, color: "#123456" },
    { id: "custom-personal", serverId: 2, label: "personal", fixed: false, enabled: true, color: "#123456" },
    { id: "custom-hidden", serverId: 3, label: "hidden", fixed: false, enabled: false, color: "#123456" },
  ]);
  api.getCalendarEvents.mockResolvedValue([]);
  jest.spyOn(window, "alert").mockImplementation(() => {});
});
afterEach(() => jest.restoreAllMocks());
test("settings dialog includes disabled categories while calendar filters exclude them", async () => {
  render(<CalendarDataProvider userId={1}><CalendarPage /></CalendarDataProvider>);
  await screen.findByRole("button", { name: /personal/ });
  expect(screen.queryByRole("button", { name: "hidden" })).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "카테고리 설정" }));
  expect(await screen.findByDisplayValue("hidden")).toBeInTheDocument();
});
test("one-sided schedule shows its known time and unknown bound on the calendar", async () => {
  api.getCalendarEvents.mockResolvedValue([{ id: "1", title: "partial event", date: "2026-10-16", endDate: "2026-10-18",
    startTime: "10:00", endTime: "", allDay: false, category: "custom-personal", description: "", editable: true }]);
  render(<CalendarDataProvider userId={1}><CalendarPage /></CalendarDataProvider>);
  await waitFor(() => expect(screen.getByTestId("calendar")).toHaveTextContent("AM 10:00 – 종료 시간 미정"));
});

const timeShapes = [
  ["normal", "09:00", "10:00"],
  ["all-day", null, null],
  ["start-only", "10:00", null],
  ["end-only", null, "11:00"],
  ["off-grid", "09:07", "10:22"],
];

async function openExistingEditor(startTime, endTime) {
  const normalize = jest.requireActual("../api/calendarApi").normalizeCalendarEvent;
  const original = { id: 1, title: "원본 일정", startDate: "2026-10-16", endDate: "2026-10-18",
    startTime, endTime, category: "CUSTOM-PERSONAL", description: "  원본 설명  ", editable: true };
  let stored = { ...original };
  api.getCalendarEvents.mockImplementation(async () => [normalize(stored)]);
  api.updateCalendarEvent.mockImplementation(async (id, payload) => {
    stored = { ...stored, ...payload };
    return normalize(stored);
  });
  render(<CalendarDataProvider userId={1}><CalendarPage /></CalendarDataProvider>);
  fireEvent.click(screen.getByRole("button", { name: "이번 달 전체" }));
  fireEvent.click(await screen.findByRole("button", { name: /^원본 일정,.*상세 보기$/ }));
  fireEvent.click(screen.getByRole("button", { name: "일정 수정", exact: true }));
  return { original, readStored: () => stored };
}

test.each(timeShapes)("title-only edit preserves the %s time pair and every other field", async (_, start, end) => {
  const { original, readStored } = await openExistingEditor(start, end);
  expect(screen.getByLabelText("시작 시간 *")).toHaveValue(start || "");
  expect(screen.getByLabelText("종료 시간 *")).toHaveValue(end || "");
  fireEvent.change(screen.getByLabelText("일정 제목 *"), { target: { value: "제목만 수정" } });
  fireEvent.click(screen.getByRole("button", { name: "변경 내용 저장" }));
  await waitFor(() => expect(api.updateCalendarEvent).toHaveBeenCalledTimes(1));
  expect(api.updateCalendarEvent).toHaveBeenCalledWith("1", { title: "제목만 수정" });
  expect(readStored()).toEqual({ ...original, title: "제목만 수정" });
  await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
  // Read the saved response again through the list and editor, including non-option times.
  fireEvent.click(screen.getByRole("button", { name: "이번 달 전체" }));
  fireEvent.click(screen.getByRole("button", { name: /^제목만 수정,.*상세 보기$/ }));
  fireEvent.click(screen.getByRole("button", { name: "일정 수정", exact: true }));
  expect(screen.getByLabelText("시작 시간 *")).toHaveValue(start || "");
  expect(screen.getByLabelText("종료 시간 *")).toHaveValue(end || "");
  expect(screen.getByLabelText(/일정 내용/)).toHaveValue(original.description);
});

test("explicit time edits still require both bounds on the 15-minute grid", async () => {
  await openExistingEditor(null, null);
  fireEvent.change(screen.getByLabelText("시작 시간 *"), { target: { value: "09:00" } });
  fireEvent.click(screen.getByRole("button", { name: "변경 내용 저장" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("종료 시간을 15분 단위로 선택해 주세요");
  expect(api.updateCalendarEvent).not.toHaveBeenCalled();
  fireEvent.change(screen.getByLabelText("종료 시간 *"), { target: { value: "10:00" } });
  fireEvent.click(screen.getByRole("button", { name: "변경 내용 저장" }));
  await waitFor(() => expect(api.updateCalendarEvent).toHaveBeenCalledWith("1", { startTime: "09:00", endTime: "10:00" }));
});

test("non-time edits preserve missing times while date validation still applies", async () => {
  const { original, readStored } = await openExistingEditor(null, null);
  fireEvent.change(screen.getByLabelText("종료일 *"), { target: { value: "2026-10-15" } });
  fireEvent.click(screen.getByRole("button", { name: "변경 내용 저장" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("종료일은 시작일과 같거나 늦어야 해요");
  expect(api.updateCalendarEvent).not.toHaveBeenCalled();
  fireEvent.change(screen.getByLabelText("종료일 *"), { target: { value: original.endDate } });
  fireEvent.change(screen.getByLabelText("시작일 *"), { target: { value: "2026-10-17" } });
  fireEvent.change(screen.getByLabelText(/일정 내용/), { target: { value: "변경 설명" } });
  fireEvent.click(screen.getByRole("button", { name: "변경 내용 저장" }));
  await waitFor(() => expect(api.updateCalendarEvent).toHaveBeenCalledWith("1", { startDate: "2026-10-17", description: "변경 설명" }));
  expect(readStored()).toEqual({ ...original, startDate: "2026-10-17", description: "변경 설명" });
});

test("an original off-grid time cannot bypass validation after the other time changes", async () => {
  await openExistingEditor("09:07", "10:22");
  fireEvent.change(screen.getByLabelText("종료 시간 *"), { target: { value: "10:30" } });
  fireEvent.click(screen.getByRole("button", { name: "변경 내용 저장" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("시작 시간을 15분 단위로 선택해 주세요");
  expect(api.updateCalendarEvent).not.toHaveBeenCalled();
});

test("clearing an existing time does not introduce time removal", async () => {
  await openExistingEditor("09:00", "10:00");
  fireEvent.change(screen.getByLabelText("시작 시간 *"), { target: { value: "" } });
  fireEvent.click(screen.getByRole("button", { name: "변경 내용 저장" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("시작 시간을 15분 단위로 선택해 주세요");
  expect(api.updateCalendarEvent).not.toHaveBeenCalled();
});

test("new events still require both times and retain chronological validation", async () => {
  api.createCalendarEvent.mockImplementation(async (payload) => ({ ...payload, id: "1", date: payload.startDate, category: "custom-personal" }));
  render(<CalendarDataProvider userId={1}><CalendarPage /></CalendarDataProvider>);
  await screen.findByRole("button", { name: "personal", exact: true });
  fireEvent.click(screen.getByRole("button", { name: "일정 등록" }));
  const submit = within(screen.getByRole("dialog")).getByRole("button", { name: "일정 등록", exact: true });
  fireEvent.change(screen.getByLabelText("일정 제목 *"), { target: { value: "새 일정" } });
  fireEvent.change(screen.getByLabelText("일정 분류 *"), { target: { value: "custom-personal" } });
  fireEvent.change(screen.getByLabelText("시작일 *"), { target: { value: "2026-10-16" } });
  fireEvent.change(screen.getByLabelText("종료일 *"), { target: { value: "2026-10-16" } });
  fireEvent.change(screen.getByLabelText("시작 시간 *"), { target: { value: "" } });
  fireEvent.change(screen.getByLabelText("종료 시간 *"), { target: { value: "10:00" } });
  fireEvent.click(submit);
  expect(await screen.findByRole("alert")).toHaveTextContent("시작 시간을 15분 단위로 선택해 주세요");
  expect(api.createCalendarEvent).not.toHaveBeenCalled();
  fireEvent.change(screen.getByLabelText("시작 시간 *"), { target: { value: "10:00" } });
  fireEvent.click(submit);
  expect(await screen.findByRole("alert")).toHaveTextContent("종료 시간은 시작 시간보다 늦어야 해요");
  expect(api.createCalendarEvent).not.toHaveBeenCalled();
  fireEvent.change(screen.getByLabelText("종료 시간 *"), { target: { value: "10:15" } });
  fireEvent.click(submit);
  await waitFor(() => expect(api.createCalendarEvent).toHaveBeenCalledWith({ title: "새 일정", startDate: "2026-10-16", endDate: "2026-10-16", startTime: "10:00", endTime: "10:15", categoryId: 2, description: "" }));
});
