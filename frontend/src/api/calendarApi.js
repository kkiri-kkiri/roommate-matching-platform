import axios from "axios";

const apiClient = axios.create({
  baseURL: process.env.REACT_APP_API_BASE_URL || "http://localhost:8080/api",
  timeout: 15000,
});

// Public school/dormitory schedules are available without signing in.
export async function getPublicCalendarEvents(from, to, signal) {
  const response = await apiClient.get("/calendar/events", {
    params: { from, to, categories: "SCHOOL,DORMITORY" },
    signal,
  });
  return response.data.data.map((event) => ({
    ...event,
    id: `server-${event.id}`,
    date: event.startDate,
    category: event.category.toLowerCase(),
    startTime: event.startTime || "",
    endTime: event.endTime || "",
    allDay: !event.startTime && !event.endTime,
    description: event.description || "",
    editable: false,
  }));
}
