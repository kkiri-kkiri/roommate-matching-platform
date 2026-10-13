import { createApiClient } from "./apiClient";
import { getAccessToken } from "../auth/session";

const apiClient = createApiClient({
  baseURL: process.env.REACT_APP_API_BASE_URL || "http://localhost:8080/api",
  headers: { "Content-Type": "application/json" },
});

async function request(config) {
  try {
    const accessToken = getAccessToken();
    if (!accessToken) throw new Error("로그인 후 이용해주세요.");
    const response = await apiClient.request({
      ...config, headers: { Authorization: `Bearer ${accessToken}` },
    });
    return response.data.data;
  } catch (error) {
    throw new Error(error.response?.data?.message || error.message || "채팅 요청에 실패했습니다.");
  }
}

export const getChatRooms = ({ category = "ALL", q } = {}) =>
  request({ method: "get", url: "/chat/rooms", params: { category, q } });
export const createChatRoom = (payload) =>
  request({ method: "post", url: "/chat/rooms", data: payload });
export const getChatMessages = (roomId, { page = 0, size = 50, q } = {}) =>
  request({ method: "get", url: `/chat/rooms/${roomId}/messages`, params: { page, size, q } });
export const markChatRoomRead = (roomId) =>
  request({ method: "post", url: `/chat/rooms/${roomId}/read` });
export const updateChatRoomSettings = (roomId, payload) =>
  request({ method: "patch", url: `/chat/rooms/${roomId}/settings`, data: payload });
export const leaveChatRoom = (roomId) =>
  request({ method: "delete", url: `/chat/rooms/${roomId}/members/me` });
