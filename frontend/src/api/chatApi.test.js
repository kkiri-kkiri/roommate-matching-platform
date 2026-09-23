import axios from "axios";
import { getChatRooms, createChatRoom, getChatMessages, markChatRoomRead,
  updateChatRoomSettings, leaveChatRoom } from "./chatApi";

jest.mock("axios", () => ({ create: jest.fn(() => ({ request: jest.fn() })) }));
const request = axios.create.mock.results[0].value.request;

beforeEach(() => {
  request.mockReset().mockResolvedValue({ data: { data: { id: 1 } } });
  localStorage.setItem("accessToken", "access-test");
});

test.each([
  [() => getChatRooms({ category: "COMMUNITY", q: "이웃" }), "get", "/chat/rooms", { params: { category: "COMMUNITY", q: "이웃" } }],
  [() => createChatRoom({ targetUserId: 2, category: "ROOMMATE" }), "post", "/chat/rooms", { data: { targetUserId: 2, category: "ROOMMATE" } }],
  [() => getChatMessages(1, { q: "청소" }), "get", "/chat/rooms/1/messages", { params: { page: 0, size: 50, q: "청소" } }],
  [() => markChatRoomRead(1), "post", "/chat/rooms/1/read", {}],
  [() => updateChatRoomSettings(1, { pinned: true }), "patch", "/chat/rooms/1/settings", { data: { pinned: true } }],
  [() => leaveChatRoom(1), "delete", "/chat/rooms/1/members/me", {}],
])("REST contract %#", async (call, method, url, extra) => {
  await expect(call()).resolves.toEqual({ id: 1 });
  expect(request).toHaveBeenCalledWith({ method, url, ...extra, headers: { Authorization: "Bearer access-test" } });
});

test("uses the current token and exposes the server error", async () => {
  localStorage.setItem("accessToken", "new-token");
  request.mockRejectedValue({ response: { data: { message: "참여자가 아닙니다." } } });
  await expect(getChatRooms()).rejects.toThrow("참여자가 아닙니다.");
  expect(request.mock.calls[0][0].headers.Authorization).toBe("Bearer new-token");
});

test("does not request without an access token", async () => {
  localStorage.removeItem("accessToken");
  await expect(getChatRooms()).rejects.toThrow("로그인");
  expect(request).not.toHaveBeenCalled();
});
