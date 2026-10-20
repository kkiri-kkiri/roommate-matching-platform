import { getChatRoom, getChatRooms, getChatMessages, getChatReadStatuses, markChatRoomRead } from "./chatApi";
import { createApiClient } from "./apiClient";
import { getAccessToken } from "../auth/session";

jest.mock("./apiClient", () => ({ createApiClient: jest.fn(() => ({ request: jest.fn() })) }));
jest.mock("../auth/session", () => ({ getAccessToken: jest.fn() }));

const client = createApiClient.mock.results[0].value;

beforeEach(() => {
  client.request.mockReset();
  getAccessToken.mockReturnValue("test-token");
});

test("keeps the offset request shape and adds a cursor only when requested", async () => {
  client.request.mockResolvedValue({ data: { data: { content: [] } } });
  await getChatMessages("10");
  expect(client.request.mock.calls[0][0].params).toEqual({ page: 0, size: 50, q: undefined });
  await getChatMessages("10", { q: "hi", beforeId: 50 });
  expect(client.request.mock.calls[1][0].params).toEqual({ page: 0, size: 50, q: "hi", beforeId: 50 });
  await getChatReadStatuses("10", [5, 6]);
  expect(client.request).toHaveBeenLastCalledWith({ method: "post", url: "/chat/rooms/10/messages/read-status",
    data: { messageIds: [5, 6] }, headers: { Authorization: "Bearer test-token" } });
});

test("requests bounded room pages with server filters and retains metadata", async () => {
  const page = { content: [], page: 1, size: 50, totalElements: 60, last: false,
    counts: { all: 90, roommate: 30, community: 60, pinned: 2 } };
  client.request.mockResolvedValue({ data: { success: true, data: page } });
  await expect(getChatRooms({ category: "COMMUNITY", q: "익명", page: 1 })).resolves.toEqual(page);
  expect(client.request).toHaveBeenCalledWith({ method: "get", url: "/chat/rooms",
    params: { category: "COMMUNITY", q: "익명", page: 1, size: 50 },
    headers: { Authorization: "Bearer test-token" } });
});

test("fetches a selected room independently from the loaded list page", async () => {
  client.request.mockResolvedValue({ data: { success: true, data: { id: 99 } } });
  await expect(getChatRoom("99")).resolves.toEqual({ id: 99 });
  expect(client.request).toHaveBeenCalledWith({ method: "get", url: "/chat/rooms/99",
    headers: { Authorization: "Bearer test-token" } });
});

test("sends exact read receipt IDs with the existing auth session and unwraps the response", async () => {
  client.request.mockResolvedValue({ data: { success: true, data: null } });
  getAccessToken.mockReturnValue("test-token");
  await expect(markChatRoomRead("10", [42, 43])).resolves.toBeNull();
  expect(client.request).toHaveBeenCalledWith({
    method: "post", url: "/chat/rooms/10/read", data: { messageIds: [42, 43] },
    headers: { Authorization: "Bearer test-token" },
  });
});
