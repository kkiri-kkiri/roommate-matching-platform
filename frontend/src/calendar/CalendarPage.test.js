import { fireEvent, render, screen, waitFor } from "@testing-library/react";
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
});
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
