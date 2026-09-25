import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import { getPublicCalendarEvents } from "../api/calendarApi";
import * as S from "./CalendarPageCss";

export { dateKey } from "./calendarDates";

const CATEGORIES = [
  { id: "school", label: "학교 일정" },
  { id: "dormitory", label: "기숙사 일정" },
  { id: "roommate", label: "룸메찾기 일정" },
  { id: "personal", label: "개인 일정" },
];
export const FIXED_CATEGORY_IDS = ["school", "dormitory"];
const CATEGORY_COLORS_KEY = "calendar-category-colors";
const CATEGORY_SETTINGS_KEY = "calendar-category-settings";

function loadCategorySettings() {
  let saved = {};
  try { saved = JSON.parse(localStorage.getItem(CATEGORY_COLORS_KEY)) || {}; } catch {}
  const defaults = CATEGORIES.map((category) => ({ ...category,
    color: /^#[0-9a-f]{6}$/i.test(saved[category.id]) ? saved[category.id] : S.CATEGORY_STYLES[category.id].color,
  }));
  try {
    const settings = JSON.parse(localStorage.getItem(CATEGORY_SETTINGS_KEY));
    if (!Array.isArray(settings)) return defaults;
    const valid = settings.filter((item, index) => item && typeof item.id === "string" &&
      /^[a-z0-9-]+$/.test(item.id) && typeof item.label === "string" && item.label.trim() &&
      item.label.length <= 30 && /^#[0-9a-f]{6}$/i.test(item.color) &&
      settings.findIndex((other) => other?.id === item.id) === index);
    return [...defaults.filter(({ id }) => FIXED_CATEGORY_IDS.includes(id)).map((item) => ({
      ...item, color: valid.find(({ id }) => id === item.id)?.color || item.color,
    })), ...valid.filter(({ id }) => !FIXED_CATEGORY_IDS.includes(id))];
  } catch { return defaults; }
}

const CalendarDataContext = createContext(null);

export function CalendarDataProvider({ children }) {
  const [categorySettings, setCategorySettings] = useState(loadCategorySettings);
  const [localEvents, setEvents] = useState([]);
  const [remoteEvents, setRemoteEvents] = useState([]);
  const [range, setRange] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [revision, setRevision] = useState(0);
  const retry = useCallback(() => setRevision((current) => current + 1), []);
  const requestRange = useCallback((from, to) => {
    setRange((current) => current?.from === from && current?.to === to ? current : { from, to });
  }, []);

  useEffect(() => {
    if (!range) return undefined;
    const controller = new AbortController();
    setLoading(true);
    setError("");
    setRemoteEvents([]);
    getPublicCalendarEvents(range.from, range.to, controller.signal)
      .then((items) => {
        if (!controller.signal.aborted) setRemoteEvents(items);
      })
      .catch(() => {
        if (!controller.signal.aborted) setError("일정을 불러오지 못했어요. 백엔드 연결을 확인하고 다시 시도해 주세요.");
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [range, revision]);

  const events = useMemo(() => [...remoteEvents, ...localEvents], [remoteEvents, localEvents]);

  useEffect(() => {
    try { localStorage.setItem(CATEGORY_SETTINGS_KEY, JSON.stringify(categorySettings)); } catch {}
  }, [categorySettings]);

  const value = useMemo(() => ({ events, setEvents, categorySettings, setCategorySettings, loading, error, retry, requestRange }),
    [events, categorySettings, loading, error, retry, requestRange]);

  return <CalendarDataContext.Provider value={value}>{children}</CalendarDataContext.Provider>;
}

export function useCalendarData(from, to) {
  const value = useContext(CalendarDataContext);
  const requestRange = value?.requestRange;
  useEffect(() => {
    if (from && to) requestRange?.(from, to);
  }, [from, to, requestRange]);
  if (!value) throw new Error("useCalendarData requires CalendarDataProvider");
  return value;
}
