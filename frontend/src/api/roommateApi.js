import axios from "axios";

const apiClient = axios.create({
  baseURL: process.env.REACT_APP_API_BASE_URL || "http://localhost:8080/api",
  headers: { "Content-Type": "application/json" },
  timeout: 60000,
});

async function request(config) {
  const accessToken = window.localStorage.getItem("accessToken");
  if (!accessToken) throw new Error("로그인 후 이용해주세요.");
  try {
    const response = await apiClient.request({
      ...config,
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    return response.data.data;
  } catch (error) {
    if (error.code === "ERR_CANCELED") throw error;
    throw new Error(error.response?.data?.message || "요청에 실패했습니다. 잠시 후 다시 시도해 주세요.");
  }
}

export const getRecommendedRoommates = (signal) =>
  request({ method: "get", url: "/roommates/recommended", params: { page: 0, size: 9 }, signal });

export const getRoommateDetail = (userId, signal) =>
  request({ method: "get", url: `/roommates/${userId}`, signal });

export const analyzeRoommateProfile = async (userId, signal) => {
  const data = await request({ method: "post", url: `/roommates/${userId}/ai-analysis`, signal });
  if (data?.targetUserId !== userId || typeof data?.analysis !== "string" || !data.analysis.trim()
      || !Number.isInteger(data.matchScore) || data.matchScore < 0 || data.matchScore > 100) {
    throw new Error("AI 분석 응답을 확인할 수 없습니다. 다시 시도해 주세요.");
  }
  return data;
};
