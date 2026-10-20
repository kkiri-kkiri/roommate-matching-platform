import { useState } from "react";
import { act, renderHook, waitFor } from "@testing-library/react";
import { useChatMessages } from "./useChatMessages";
import { getChatMessages, getChatReadStatuses } from "../../api/chatApi";

jest.mock("../../api/chatApi");
const message = (id, extra = {}) => ({ id, senderType: "OTHER", text: `m${id}`,
  sentAt: "2026-10-19T12:00:00", read: false, ...extra });
const page = (ids, last = false) => ({ content: ids.map((id) => message(id)), last });
const setStatus = jest.fn();
function useHarness({ id = "1", q = "", visibleMessageIds } = {}) {
  const [rooms, setRooms] = useState([{ id: "1", messages: [] }, { id: "2", messages: [] }]);
  const result = useChatMessages({ activeId: id, counterpartId: 10, searchQuery: q, setRooms, setStatus,
    displayedMessages: rooms.find((room) => room.id === id)?.messages, visibleMessageIds });
  return { ...result, rooms };
}
beforeEach(() => { jest.resetAllMocks(); getChatMessages.mockResolvedValue(page([3, 2], false));
  getChatReadStatuses.mockResolvedValue([]); });

test("loads older pages once, retains new arrivals, deduplicates and stops at last page", async () => {
  getChatMessages.mockResolvedValueOnce(page([3, 2])).mockResolvedValueOnce(page([4], true))
    .mockResolvedValueOnce(page([2, 1], true));
  const { result } = renderHook(useHarness);
  await waitFor(() => expect(result.current.messageLoading).toBe(false));
  await act(async () => result.current.syncMessages());
  expect(getChatMessages).toHaveBeenLastCalledWith("1", { page: 0, size: 50, q: undefined, afterId: 3 });
  await act(async () => Promise.all([result.current.loadOlderMessages(), result.current.loadOlderMessages()]));
  expect(getChatMessages).toHaveBeenCalledTimes(3);
  expect(getChatMessages).toHaveBeenLastCalledWith("1", { page: 0, size: 50, q: undefined, beforeId: 2 });
  expect(result.current.rooms[0].messages.map((item) => item.id)).toEqual([1, 2, 3, 4]);
  await act(async () => result.current.loadOlderMessages());
  expect(getChatMessages).toHaveBeenCalledTimes(3);
});

test("retries a failed older cursor without removing existing history", async () => {
  getChatMessages.mockResolvedValueOnce(page([3, 2])).mockRejectedValueOnce(new Error("offline"))
    .mockResolvedValue(page([1], true));
  const { result } = renderHook(useHarness);
  await waitFor(() => expect(result.current.messageLoading).toBe(false));
  await act(async () => result.current.loadOlderMessages());
  expect(result.current.moreMessagesError).toBe("offline");
  expect(result.current.rooms[0].messages).toHaveLength(2);
  await act(async () => result.current.loadOlderMessages());
  expect(getChatMessages.mock.calls.slice(1).map(([, request]) => request.beforeId)).toEqual([2, 2]);
  expect(result.current.rooms[0].messages).toHaveLength(3);
});

test("search pagination resets on a new term or room, ignoring a late old page", async () => {
  let finish;
  getChatMessages.mockResolvedValueOnce(page([3, 2]))
    .mockImplementationOnce(() => new Promise((resolve) => { finish = resolve; }))
    .mockResolvedValue(page([9], true));
  const { result, rerender } = renderHook(useHarness, { initialProps: { q: "old" } });
  await waitFor(() => expect(result.current.searchResults).toHaveLength(2));
  act(() => { void result.current.loadOlderMessages(); });
  rerender({ id: "2", q: "new" });
  await waitFor(() => expect(result.current.searchResults.map((item) => item.id)).toEqual([9]));
  await act(async () => finish(page([1], true)));
  expect(result.current.searchResults.map((item) => item.id)).toEqual([9]);
  expect(getChatMessages).toHaveBeenLastCalledWith("2", { page: 0, size: 50, q: "new" });
});

test("polls only visible own receipts and never acknowledges a fetched message", async () => {
  getChatMessages.mockResolvedValue({ content: [message(3, { senderType: "ME" }), message(2, { senderType: "ME" })], last: true });
  getChatReadStatuses.mockResolvedValue([{ id: 3, read: true }]);
  const { result } = renderHook(useHarness, { initialProps: { visibleMessageIds: ["3"] } });
  await waitFor(() => expect(result.current.rooms[0].messages.find((item) => item.id === 3)?.read).toBe(true));
  expect(getChatReadStatuses).toHaveBeenCalledWith("1", [3]);
  expect(result.current.rooms[0].messages.find((item) => item.id === 2).read).toBe(false);
});

test("a forbidden resync removes a room left on another client", async () => {
  getChatMessages.mockResolvedValueOnce(page([3], true)).mockRejectedValue(Object.assign(new Error("forbidden"), { status: 403 }));
  const { result } = renderHook(useHarness);
  await waitFor(() => expect(result.current.messageLoading).toBe(false));
  await act(async () => result.current.syncMessages());
  expect(result.current.rooms.some((room) => room.id === "1")).toBe(false);
});
