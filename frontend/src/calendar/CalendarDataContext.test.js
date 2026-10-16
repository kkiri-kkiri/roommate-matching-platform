import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import "@testing-library/jest-dom";
import { useRef } from "react";
import { CalendarDataProvider, useCalendarData } from "./CalendarDataContext";
import CategorySettingsForm from "./CategorySettingsForm";
import * as api from "../api/calendarApi";
jest.mock("../api/calendarApi");

let data;
const initial = [
  { id: "school", serverId: 1, label: "학교", fixed: true, color: "#123456", enabled: true },
  { id: "custom-visible", serverId: 2, label: "visible", fixed: false, color: "#123456", enabled: true },
  { id: "custom-hidden", serverId: 3, label: "hidden", fixed: false, color: "#654321", enabled: false },
];
function Probe() {
  data = useCalendarData("2026-10-01", "2026-10-31");
  const savingRef = useRef(false);
  if (!data.categorySettings.some(c => c.serverId)) return null;
  return <CategorySettingsForm initialCategories={data.categorySettings}
    events={data.events} onSave={data.saveCategories} onDismiss={() => {}} savingRef={savingRef} />;
}
async function setup() {
  render(<CalendarDataProvider userId={1}><Probe /></CalendarDataProvider>);
  await screen.findByDisplayValue("hidden");
}
beforeEach(() => {
  jest.resetAllMocks();
  api.getCalendarEvents.mockResolvedValue([]);
  api.getCalendarCategories.mockResolvedValue(initial.map(c => ({ ...c })));
  api.deleteCalendarCategory.mockResolvedValue(undefined);
  api.saveCalendarCategories.mockImplementation(async next => [
    ...initial.filter(old => !next.some(c => c.serverId === old.serverId)), ...next,
  ]);
});
test("omitted disabled category is preserved without a DELETE", async () => {
  await setup();
  await act(async () => data.saveCategories(data.categorySettings.filter(c => c.enabled !== false)));
  expect(api.deleteCalendarCategory).not.toHaveBeenCalled();
  expect(data.categorySettings).toEqual(expect.arrayContaining([expect.objectContaining({ serverId: 3, enabled: false })]));
});
test("color edit preserves other names, colors and visibility", async () => {
  await setup(); fireEvent.change(screen.getByLabelText("visible 색상 설정"), { target: { value: "#abcdef" } });
  fireEvent.click(screen.getByRole("button", { name: "설정 저장" }));
  await waitFor(() => expect(api.saveCalendarCategories).toHaveBeenCalled());
  expect(api.deleteCalendarCategory).not.toHaveBeenCalled();
  expect(api.saveCalendarCategories.mock.calls[0][0]).toEqual(expect.arrayContaining([
    expect.objectContaining({ serverId: 2, color: "#abcdef" }), initial[0], initial[2],
  ]));
});
test("only an explicit removal sends DELETE", async () => {
  await setup(); fireEvent.click(screen.getByRole("button", { name: "hidden 삭제" }));
  fireEvent.click(screen.getByRole("button", { name: "설정 저장" }));
  await waitFor(() => expect(api.deleteCalendarCategory).toHaveBeenCalledWith(3));
  expect(api.deleteCalendarCategory).toHaveBeenCalledTimes(1);
});
test("omitting an active category also never implies deletion", async () => {
  await setup(); await act(async () => data.saveCategories([initial[0]]));
  expect(api.deleteCalendarCategory).not.toHaveBeenCalled();
});
test("visibility change preserves other categories and never deletes", async () => {
  await setup(); fireEvent.click(screen.getByRole("checkbox", { name: "hidden 표시" }));
  fireEvent.click(screen.getByRole("button", { name: "설정 저장" }));
  await waitFor(() => expect(api.saveCalendarCategories).toHaveBeenCalled());
  expect(api.deleteCalendarCategory).not.toHaveBeenCalled();
  expect(api.saveCalendarCategories.mock.calls[0][0]).toEqual(expect.arrayContaining([
    initial[0], initial[1], expect.objectContaining({ serverId: 3, enabled: true, color: "#654321" }),
  ]));
});
test("category with an event cannot be explicitly removed", async () => {
  api.getCalendarEvents.mockResolvedValue([{ id: "event", category: "custom-visible" }]);
  await setup(); expect(screen.getByRole("button", { name: "visible 삭제" })).toBeDisabled();
  fireEvent.click(screen.getByRole("button", { name: "visible 삭제" }));
  fireEvent.click(screen.getByRole("button", { name: "설정 저장" }));
  await waitFor(() => expect(api.saveCalendarCategories).toHaveBeenCalled());
  expect(api.deleteCalendarCategory).not.toHaveBeenCalled();
});
test("retry after a completed deletion and failed PUT does not delete twice", async () => {
  const remaining = initial.filter(c => c.serverId !== 3);
  api.getCalendarCategories.mockResolvedValueOnce(initial).mockResolvedValue(remaining);
  api.saveCalendarCategories.mockRejectedValueOnce(new Error("temporary failure")).mockResolvedValueOnce(remaining);
  await setup(); fireEvent.click(screen.getByRole("button", { name: "hidden 삭제" }));
  fireEvent.click(screen.getByRole("button", { name: "설정 저장" }));
  await screen.findByText("temporary failure");
  fireEvent.click(screen.getByRole("button", { name: "설정 저장" }));
  await waitFor(() => expect(api.saveCalendarCategories).toHaveBeenCalledTimes(2));
  expect(api.deleteCalendarCategory).toHaveBeenCalledTimes(1);
});
test("new disabled category retains the requested visibility after creation", async () => {
  api.createCalendarCategory.mockResolvedValue({ id: "custom-new", serverId: 4, label: "new", color: "#006a65", enabled: true });
  await setup(); fireEvent.click(screen.getByRole("button", { name: "카테고리 추가" }));
  fireEvent.change(screen.getByDisplayValue(""), { target: { value: "new" } });
  fireEvent.click(screen.getByRole("checkbox", { name: "new 표시" }));
  fireEvent.click(screen.getByRole("button", { name: "설정 저장" }));
  await waitFor(() => expect(api.saveCalendarCategories).toHaveBeenCalled());
  expect(api.saveCalendarCategories.mock.calls[0][0]).toEqual(expect.arrayContaining([expect.objectContaining({ serverId: 4, enabled: false })]));
});
