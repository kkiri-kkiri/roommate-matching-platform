import { useRef, useState } from "react";
import { act, renderHook, waitFor } from "@testing-library/react";
import { useChatRooms } from "./useChatRooms";
import { getChatRoom, getChatRooms } from "../../api/chatApi";

jest.mock("../../api/chatApi");
const counts = { all: 120, roommate: 70, community: 50, pinned: 2 };
const item = (id) => ({ id, pinned: false, muted: false, nickname: `user${id}` });
const page = (ids, number = 0, last = true) => ({ content: ids.map(item), page: number,
  size: 50, totalElements: 120, totalPages: 3, first: number === 0, last, counts });
const mapRoom = (room) => ({ ...room, id: String(room.id) });
const setStatus = jest.fn();

function useHarness({ category = "all", q = "", selectedRoomId = "", locationKey = "start" } = {}) {
  const [rooms, setRooms] = useState([]);
  const revisionRef = useRef(0);
  const mutationRef = useRef(new Set());
  return { rooms, ...useChatRooms({ isAuthenticated: true, rooms, setRooms, selectedRoomId,
    category, q, locationKey, revisionRef, mutationRef, mapRoom, setStatus }) };
}

beforeEach(() => { jest.resetAllMocks(); getChatRooms.mockResolvedValue(page([1, 2], 0, false)); });

test("appends server pages in order, deduplicates IDs and uses global counts", async () => {
  getChatRooms.mockResolvedValueOnce(page([1, 2], 0, false)).mockResolvedValue(page([2, 3], 1));
  const { result } = renderHook(useHarness);
  await waitFor(() => expect(result.current.loadingRooms).toBe(false));
  expect(result.current.roomCounts).toEqual(counts);
  await act(async () => { await Promise.all([result.current.loadMoreRooms(), result.current.loadMoreRooms()]); });
  expect(getChatRooms).toHaveBeenCalledTimes(2);
  expect(getChatRooms).toHaveBeenLastCalledWith({ category: "ALL", q: undefined, page: 1, size: 50 });
  expect(result.current.roomIds).toEqual(["1", "2", "3"]);
  expect(result.current.hasMoreRooms).toBe(false);
});

test("filter changes reset pagination and ignore a stale next page", async () => {
  let finishOld;
  getChatRooms.mockResolvedValueOnce(page([1, 2], 0, false))
    .mockImplementationOnce(() => new Promise((resolve) => { finishOld = resolve; }))
    .mockResolvedValue(page([99]));
  const { result, rerender } = renderHook(useHarness);
  await waitFor(() => expect(result.current.loadingRooms).toBe(false));
  act(() => { void result.current.loadMoreRooms(); });
  await waitFor(() => expect(finishOld).toBeDefined());
  rerender({ category: "community", q: " 익명 " });
  await waitFor(() => expect(result.current.roomIds).toEqual(["99"]));
  expect(getChatRooms).toHaveBeenLastCalledWith({ category: "COMMUNITY", q: "익명", page: 0, size: 50 });
  await act(async () => finishOld(page([3], 1)));
  expect(result.current.roomIds).toEqual(["99"]);
  expect(result.current.rooms.some((room) => room.id === "3")).toBe(false);
});

test("a failed next page preserves loaded rooms and retries the same page", async () => {
  getChatRooms.mockResolvedValueOnce(page([1], 0, false)).mockRejectedValueOnce(new Error("retry"))
    .mockResolvedValue(page([2], 1));
  const { result } = renderHook(useHarness);
  await waitFor(() => expect(result.current.loadingRooms).toBe(false));
  await act(async () => result.current.loadMoreRooms());
  expect(result.current.roomIds).toEqual(["1"]);
  expect(result.current.moreRoomsError).toBe("retry");
  await act(async () => result.current.loadMoreRooms());
  expect(getChatRooms.mock.calls.slice(1).map(([request]) => request.page)).toEqual([1, 1]);
  expect(result.current.roomIds).toEqual(["1", "2"]);
  expect(result.current.moreRoomsError).toBe("");
});

test("deep links load a room outside the page without inserting it into filtered list IDs", async () => {
  getChatRoom.mockResolvedValue(item(99));
  const { result } = renderHook(useHarness, { initialProps: { selectedRoomId: "99" } });
  await waitFor(() => expect(result.current.rooms.some((room) => room.id === "99")).toBe(true));
  await waitFor(() => expect(result.current.loadingRooms).toBe(false));
  expect(getChatRoom).toHaveBeenCalledWith("99");
  expect(result.current.roomIds).toEqual(["1", "2"]);
});

test("stale selected-room responses cannot restore a previously selected room", async () => {
  let finishOld;
  getChatRoom.mockImplementationOnce(() => new Promise((resolve) => { finishOld = resolve; }))
    .mockResolvedValue(item(100));
  const { result, rerender } = renderHook(useHarness, { initialProps: { selectedRoomId: "99" } });
  rerender({ selectedRoomId: "100" });
  await waitFor(() => expect(result.current.rooms.some((room) => room.id === "100")).toBe(true));
  await act(async () => finishOld(item(99)));
  expect(result.current.rooms.some((room) => room.id === "99")).toBe(false);
});

test("background sync preserves loaded pages with a stable prefix and resets changed order safely", async () => {
  getChatRooms.mockResolvedValueOnce(page([1, 2], 0, false)).mockResolvedValueOnce(page([3, 4], 1))
    .mockResolvedValueOnce(page([1, 2], 0, false)).mockResolvedValue(page([99, 1], 0, false));
  const { result } = renderHook(useHarness);
  await waitFor(() => expect(result.current.loadingRooms).toBe(false));
  await act(async () => result.current.loadMoreRooms());
  await act(async () => result.current.refreshRooms({ background: true }));
  expect(result.current.roomIds).toEqual(["1", "2", "3", "4"]);
  await act(async () => result.current.refreshRooms({ background: true }));
  expect(result.current.roomIds).toEqual(["99", "1"]);
  expect(result.current.hasMoreRooms).toBe(true);
  expect(result.current.loadingRooms).toBe(false);
});

test("an unselected or newly created room gets server preview, unread count and order without incrementing locally", async () => {
  const updated = { ...item(99), nickname: "익명", counterpartId: null, unreadCount: 3,
    lastMessage: { text: "new committed message", sentAt: "2026-10-19T12:00:00" } };
  getChatRooms.mockResolvedValueOnce(page([1], 0, false)).mockResolvedValue({ ...page([99, 1]), content: [updated, item(1)] });
  const { result } = renderHook(useHarness);
  await waitFor(() => expect(result.current.loadingRooms).toBe(false));
  await act(async () => result.current.refreshRooms({ background: true }));
  await act(async () => result.current.refreshRooms({ background: true }));
  expect(result.current.roomIds).toEqual(["99", "1"]);
  expect(result.current.rooms.find((room) => room.id === "99")).toMatchObject({ unreadCount: 3, counterpartId: null,
    lastMessage: { text: "new committed message" } });
});
