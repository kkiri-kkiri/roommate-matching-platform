import { createApiClient } from "./apiClient";
import { getAccessToken } from "../auth/session";

const apiClient = createApiClient({
  baseURL: process.env.REACT_APP_API_BASE_URL || "http://localhost:8080/api",
  headers: { "Content-Type": "application/json" },
  timeout: 60000,
});

async function request(config, authenticated = true) {
  const accessToken = getAccessToken();
  if (authenticated && !accessToken) throw new Error("로그인 후 이용해주세요.");
  try {
    const response = await apiClient.request({
      ...config,
      optionalAuth: !authenticated,
      headers: accessToken ? { Authorization: `Bearer ${accessToken}` } : {},
    });
    return response.data.data;
  } catch (error) {
    if (error.code === "ERR_CANCELED") throw error;
    throw new Error(error.response?.data?.message || "요청에 실패했습니다. 잠시 후 다시 시도해 주세요.");
  }
}

export const getRecommendedRoommates = (signal) =>
  request({ method: "get", url: "/roommates/recommended", params: { page: 0, size: 9 }, signal });

export const getFilteredRecommendedRoommates = (filters = {}, signal) => {
  const params = new URLSearchParams({ page: "0", size: "9" });
  Object.entries(filters).forEach(([key, values]) => {
    values.forEach((value) => params.append(key, value));
  });
  return request({ method: "get", url: "/roommates/recommended", params, signal });
};

export const getRoommateDetail = (userId, signal) =>
  request({ method: "get", url: `/roommates/${userId}`, signal }, false);

export const analyzeRoommateProfile = async (userId, signal) => {
  const data = await request({ method: "post", url: `/roommates/${userId}/ai-analysis`, signal });
  if (data?.targetUserId !== userId || typeof data?.analysis !== "string" || !data.analysis.trim()
      || !Number.isInteger(data.matchScore) || data.matchScore < 0 || data.matchScore > 100) {
    throw new Error("AI 분석 응답을 확인할 수 없습니다. 다시 시도해 주세요.");
  }
  return data;
};

export const getRoommates = ({ page = 0, size = 9, filters = {} } = {}, signal) => {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  Object.entries(filters).forEach(([key, values]) => {
    values.forEach((value) => params.append(key, value));
  });
  return request({ method: "get", url: "/roommates", params, signal }, false);
};

export const setRoommateLike = (userId, liked) =>
  request({ method: liked ? "post" : "delete", url: `/roommates/${userId}/likes` });

export const getMyLikedRoommates = ({ page = 0, size = 6 } = {}, signal) =>
  request({ method: "get", url: "/users/me/liked-roommates", params: { page, size }, signal });

export function toRoommateProfile(item) {
  if (!Number.isSafeInteger(item.id) || item.id <= 0) throw new Error("사용자 정보를 확인할 수 없습니다.");
  return { ...item, userId: item.id, source: "backend",
    message: item.introduction, photo: item.profileImageUrl };
}
