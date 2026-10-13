import axios from "axios";
import { createApiClient } from "./apiClient";
import { getAccessToken } from "../auth/session";

const apiClient = createApiClient({
  baseURL: process.env.REACT_APP_API_BASE_URL || "http://localhost:8080/api",
  timeout: 15000,
});

async function request(config, authenticated = true) {
  const token = getAccessToken();
  if (authenticated && !token) throw new Error("로그인 후 이용해주세요.");
  try {
    const response = await apiClient.request({ ...config, optionalAuth: !authenticated,
      headers: token ? { Authorization: `Bearer ${token}` } : {} });
    return response.data.data;
  } catch (error) {
    if (axios.isCancel(error)) throw error;
    throw new Error(error.response?.data?.message || "일정 요청에 실패했어요. 잠시 후 다시 시도해 주세요.");
  }
}

export const normalizeCalendarEvent = (event) => ({
  ...event, id: String(event.id), date: event.startDate,
  category: event.category.toLowerCase(), startTime: event.startTime || "",
  endTime: event.endTime || "", allDay: !event.startTime && !event.endTime,
  description: event.description || "",
});
const normalizeCategory = (category) => ({ ...category, serverId: category.id, id: category.code.toLowerCase() });

export async function getCalendarEvents(from, to, signal, publicOnly = false) {
  const events = await request({ method: "get", url: "/calendar/events",
    params: { from, to, ...(publicOnly ? { categories: "SCHOOL,DORMITORY" } : {}) }, signal }, false);
  return events.map(normalizeCalendarEvent);
}
export const getPublicCalendarEvents = (from, to, signal) => getCalendarEvents(from, to, signal, true);
export const createCalendarEvent = async (data) => normalizeCalendarEvent(await request({ method: "post", url: "/calendar/events", data }));
export const updateCalendarEvent = async (id, data) => normalizeCalendarEvent(await request({ method: "patch", url: `/calendar/events/${id}`, data }));
export const deleteCalendarEvent = (id) => request({ method: "delete", url: `/calendar/events/${id}` });
export const getCalendarCategories = async (signal) => (await request({ method: "get", url: "/calendar/categories", signal })).map(normalizeCategory);
export const saveCalendarCategories = async (categories) => (await request({ method: "put", url: "/calendar/categories",
  data: categories.map(({ serverId, label, color, enabled }) => ({ id: serverId, label, color, enabled: enabled !== false })),
})).map(normalizeCategory);
export const createCalendarCategory = async (data) => normalizeCategory(await request({ method: "post", url: "/calendar/categories/custom", data }));
export const deleteCalendarCategory = (id) => request({ method: "delete", url: `/calendar/categories/${id}` });
