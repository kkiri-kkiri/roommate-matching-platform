import { useState } from "react";
import { act, renderHook, waitFor } from "@testing-library/react";
import { useChat } from "./useChat";
import * as api from "../../api/chatApi";
import { createChatSocket } from "../../api/chatSocket";

jest.mock("../../api/chatApi");
jest.mock("../../api/chatSocket");
const room = (id) => ({ id, category: "ROOMMATE", counterpart: {
  id: id + 10, nickname: "이웃" + id, tags: ["비흡연"], matchScore: 85,
}, unreadCount: 2, pinned: false, muted: false, lastMessage: null });
const message = (id, text = "메시지") => ({
  id, roomId: 1, senderId: 11, text, sentAt: "2026-09-23T12:00:00", read: false,
});
let callbacks;
let receive;
let socket;
let setStatus;
let onCreated;
function useHarness({ selected = "1", searchQuery = "", state = null } = {}) {
  const [rooms, setRooms] = useState([]);
  const chat = useChat({ isAuthenticated: true, rooms, setRooms,
    activeRoom: rooms.find((item) => item.id === selected), searchQuery,
    location: { state }, setStatus, onCreated });
  return { ...chat, rooms };
}
beforeEach(() => {
  jest.resetAllMocks();
  setStatus = jest.fn();
  onCreated = jest.fn();
  api.getChatRooms.mockResolvedValue([room(1), room(2)]);
  api.getChatMessages.mockResolvedValue({ content: [] });
  api.markChatRoomRead.mockResolvedValue();
  api.updateChatRoomSettings.mockResolvedValue();
  api.leaveChatRoom.mockResolvedValue();
  socket = { connect: jest.fn(), disconnect: jest.fn(),
    subscribe: jest.fn((id, cb) => { receive = cb; return jest.fn(); }), publish: jest.fn() };
  createChatSocket.mockImplementation((options) => { callbacks = options; return socket; });
});

test("merges a broadcast racing history and removes duplicate IDs; send waits for broadcast", async () => {
  let resolveHistory;
  api.getChatMessages.mockImplementation(() => new Promise((resolve) => { resolveHistory = resolve; }));
  const { result, unmount } = renderHook(useHarness);
  await waitFor(() => expect(api.getChatMessages).toHaveBeenCalledWith("1", { page: 0, size: 50, q: undefined }));
  act(() => { receive(message(2)); receive(message(2)); });
  await act(async () => resolveHistory({ content: [
    { ...message(2), senderType: "OTHER" }, { ...message(1), senderType: "ME" },
  ] }));
  expect(result.current.rooms[0].messages.map((item) => item.id)).toEqual([1, 2]);
  expect(result.current.rooms[0].messages[0].sender).toBe("me");
  act(() => { result.current.send("1", "보내기"); });
  expect(socket.publish).toHaveBeenCalledWith("1", "보내기");
  expect(result.current.rooms[0].messages).toHaveLength(2);
  act(() => receive({ ...message(3), senderId: 99 }));
  expect(result.current.rooms[0].messages[2].sender).toBe("me");
  unmount();
  expect(socket.disconnect).toHaveBeenCalledTimes(1);
});

test("switches subscriptions, ignores stale history and reloads after reconnect", async () => {
  let resolveOld;
  api.getChatMessages.mockImplementation((id) => id === "1"
    ? new Promise((resolve) => { resolveOld = resolve; })
    : Promise.resolve({ content: [] }));
  const { result, rerender } = renderHook(useHarness, { initialProps: { selected: "1" } });
  await waitFor(() => expect(resolveOld).toBeDefined());
  const unsubscribe = socket.subscribe.mock.results[0].value;
  rerender({ selected: "2" });
  await waitFor(() => expect(api.getChatMessages).toHaveBeenCalledWith("2", expect.anything()));
  expect(unsubscribe).toHaveBeenCalledTimes(1);
  await act(async () => resolveOld({ content: [message(1)] }));
  expect(result.current.rooms[1].messages).toEqual([]);
  act(() => callbacks.onConnect());
  await waitFor(() => expect(api.getChatMessages.mock.calls.filter(([id]) => id === "2")).toHaveLength(2));
});

test("read/settings/leave only succeed after REST success and expose failures", async () => {
  api.markChatRoomRead.mockRejectedValue(new Error("읽음 실패"));
  api.updateChatRoomSettings.mockRejectedValueOnce(new Error("고정 제한"));
  api.leaveChatRoom.mockRejectedValueOnce(new Error("나가기 실패"));
  const { result } = renderHook(useHarness);
  await waitFor(() => expect(setStatus).toHaveBeenCalledWith("읽음 실패"));
  expect(result.current.rooms[0].unread).toBe(2);
  await act(async () => result.current.updateSettings("1", "pinned"));
  expect(result.current.rooms[0].pinned).toBe(false);
  expect(setStatus).toHaveBeenCalledWith("고정 제한");
  await act(async () => result.current.updateSettings("1", "pinned"));
  expect(result.current.rooms[0].pinned).toBe(true);
  await act(async () => { expect(await result.current.leave("1")).toBe(false); });
  await act(async () => { expect(await result.current.leave("1")).toBe(true); });
});

test("creates ROOMMATE using the server room ID", async () => {
  api.createChatRoom.mockResolvedValue(room(9));
  const state = { roommateProfile: { id: 19 } };
  const { result } = renderHook(useHarness, { initialProps: { state } });
  await waitFor(() => expect(onCreated).toHaveBeenCalledWith("9"));
  expect(api.createChatRoom).toHaveBeenCalledWith({ targetUserId: 19, category: "ROOMMATE" });
  expect(result.current.rooms.some((item) => item.id === "9")).toBe(true);
});

test("maps existing COMMUNITY post state and rejects missing comment IDs", async () => {
  api.createChatRoom.mockResolvedValue({ ...room(9), category: "COMMUNITY", sourcePost: 31 });
  const { rerender } = renderHook(useHarness, { initialProps: {
    state: { communityChat: { recipientId: 7, sourcePost: { id: 31, from: "게시글" } } },
  } });
  await waitFor(() => expect(onCreated).toHaveBeenCalledWith("9"));
  expect(api.createChatRoom).toHaveBeenCalledWith({
    targetUserId: 7, category: "COMMUNITY", sourcePostId: 31, sourceFrom: "POST",
  });
  rerender({ state: { communityChat: { recipientId: 7, sourcePost: { id: 31, from: "댓글" } } } });
  await waitFor(() => expect(setStatus).toHaveBeenCalledWith(expect.stringContaining("댓글 ID")));
  expect(api.createChatRoom).toHaveBeenCalledTimes(1);
});

test("searches the server without replacing the room history", async () => {
  api.getChatMessages.mockResolvedValue({ content: [{ ...message(1), senderType: "OTHER" }] });
  const { result, rerender } = renderHook(useHarness, { initialProps: { searchQuery: "" } });
  await waitFor(() => expect(result.current.rooms[0].messages).toHaveLength(1));
  api.getChatMessages.mockResolvedValue({ content: [{ ...message(8, "청소"), senderType: "OTHER" }] });
  rerender({ searchQuery: "청소" });
  await waitFor(() => expect(result.current.searchResults).toHaveLength(1));
  expect(api.getChatMessages).toHaveBeenLastCalledWith("1", { page: 0, size: 50, q: "청소" });
  expect(result.current.rooms[0].messages[0].id).toBe(1);
});
