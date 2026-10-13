import { createApiClient } from "./apiClient";
import { getAccessToken } from "../auth/session";

const apiClient = createApiClient({
  baseURL: process.env.REACT_APP_API_BASE_URL || "http://localhost:8080/api",
});

async function request(config) {
  try {
    const token = getAccessToken();
    if (!token) throw new Error("로그인 후 이용해주세요.");
    const response = await apiClient.request({
      ...config, headers: { Authorization: `Bearer ${token}` },
    });
    return response.data.data;
  } catch (error) {
    const status = error.response?.status;
    const hint = status === 401 ? "로그인이 만료되었거나 토큰이 유효하지 않습니다. 다시 로그인해주세요."
      : status === 403 ? "관리자 권한이 없습니다." : "";
    const message = error.response?.data?.message || hint || error.message || "관리자 요청에 실패했습니다.";
    throw new Error(hint && message !== hint ? `${message} (${hint})` : message);
  }
}

export const getAdminDashboard = () => request({ method: "get", url: "/admin/dashboard" });
export const getAdminMembers = ({ page = 0, size = 6, q, status, dormitory } = {}) =>
  request({ method: "get", url: "/admin/members", params: { page, size, q, status, dormitory } });
export const getAdminMember = (id) => request({ method: "get", url: `/admin/members/${id}` });
export const updateAdminMemberStatus = (id, status) =>
  request({ method: "patch", url: `/admin/members/${id}/status`, data: { status } });
export const getAdminPosts = ({ page = 0, size = 6, q, category, reported, sort = "LATEST" } = {}) =>
  request({ method: "get", url: "/admin/posts", params: { page, size, q, category, reported, sort } });
export const getAdminPost = (id) => request({ method: "get", url: `/admin/posts/${id}` });
export const deleteAdminPost = (id) => request({ method: "delete", url: `/admin/posts/${id}` });
export const getAdminComments = ({ page = 0, size = 6, q, reported } = {}) =>
  request({ method: "get", url: "/admin/comments", params: { page, size, q, reported } });
export const getAdminComment = (id) => request({ method: "get", url: `/admin/comments/${id}` });
export const deleteAdminComment = (id) => request({ method: "delete", url: `/admin/comments/${id}` });
export const getAdminReports = ({ page = 0, size = 6, q, type, status } = {}) =>
  request({ method: "get", url: "/admin/reports", params: { page, size, q, type, status } });
export const getAdminReport = (id) => request({ method: "get", url: `/admin/reports/${id}` });
export const processAdminReport = (id, action) =>
  request({ method: "patch", url: `/admin/reports/${id}`, data: { action } });
export const getAdminScheduleCrawlStatuses = () => request({ method: "get", url: "/admin/schedule-crawls/status" });
export const getAdminCrawledSchedules = ({ page = 0, size = 6, q, category } = {}) =>
  request({ method: "get", url: "/admin/crawled-schedules", params: { page, size, q, category } });
export const getAdminCrawledSchedule = (id) => request({ method: "get", url: `/admin/crawled-schedules/${id}` });
