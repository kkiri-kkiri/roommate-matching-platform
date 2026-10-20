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
    const failure = new Error(error.response?.data?.message || error.message || "채팅 요청에 실패했습니다.");
    failure.status = error.response?.status;
    failure.errorCode = error.response?.data?.errorCode;
    throw failure;
  }
}

export const getChatRooms = ({ category = "ALL", q, page = 0, size = 50 } = {}) =>
  request({ method: "get", url: "/chat/rooms", params: { category, q, page, size } });
export const getChatRoom = (roomId) =>
  request({ method: "get", url: `/chat/rooms/${roomId}` });
export const createChatRoom = (payload) =>
  request({ method: "post", url: "/chat/rooms", data: payload });
export const getChatMessages = (roomId, { page = 0, size = 50, q, beforeId, afterId } = {}) =>
  request({ method: "get", url: `/chat/rooms/${roomId}/messages`,
    params: { page, size, q, ...(beforeId == null ? {} : { beforeId }), ...(afterId == null ? {} : { afterId }) } });
export const getChatReadStatuses = (roomId, messageIds) =>
  request({ method: "post", url: `/chat/rooms/${roomId}/messages/read-status`, data: { messageIds } });
export const markChatRoomRead = (roomId, messageIds) =>
  request({ method: "post", url: `/chat/rooms/${roomId}/read`, data: { messageIds } });
export const updateChatRoomSettings = (roomId, payload) =>
  request({ method: "patch", url: `/chat/rooms/${roomId}/settings`, data: payload });
export const leaveChatRoom = (roomId) =>
  request({ method: "delete", url: `/chat/rooms/${roomId}/members/me` });
