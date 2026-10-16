import { createContext, useCallback, useContext, useEffect, useState } from "react";
import { getCalendarEvents, getCalendarCategories, createCalendarEvent, updateCalendarEvent, deleteCalendarEvent,
  saveCalendarCategories, createCalendarCategory, deleteCalendarCategory } from "../api/calendarApi";
import * as S from "./CalendarPageCss";
import { normalizeEventTimes } from "./calendarDates";
export { dateKey } from "./calendarDates";

export const FIXED_CATEGORY_IDS = ["school", "dormitory"];
const DEFAULT_CATEGORIES = [
  { id: "school", label: "학교 일정" }, { id: "dormitory", label: "기숙사 일정" },
].map((category) => ({ ...category, color: S.CATEGORY_STYLES[category.id].color, fixed: true, enabled: true }));
const CalendarDataContext = createContext(null);

export function CalendarDataProvider({ children, userId }) {
  const [categorySettings, setCategories] = useState(DEFAULT_CATEGORIES);
  const [events, setEvents] = useState([]);
  const [range, setRange] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [revision, setRevision] = useState(0);
  const retry = useCallback(() => setRevision((current) => current + 1), []);
  const requestRange = useCallback((from, to) => {
    setRange((current) => current?.from === from && current?.to === to ? current : { from, to });
  }, []);

  useEffect(() => { setCategories(DEFAULT_CATEGORIES); setEvents([]); }, [userId]);

  useEffect(() => {
    const controller = new AbortController();
    setEvents([]); setError("");
    if (!range) return () => controller.abort();
    setLoading(true);
    Promise.all([
      getCalendarEvents(range.from, range.to, controller.signal, !userId),
      userId ? getCalendarCategories(controller.signal) : Promise.resolve(DEFAULT_CATEGORIES),
    ]).then(([items, categories]) => {
      if (!controller.signal.aborted) { setEvents(items); setCategories(categories); }
    }).catch((error) => { if (!controller.signal.aborted) setError(error.message); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [range, userId, revision]);

  const saveEvent = async (draft, id) => {
    const category = categorySettings.find((item) => item.id === draft.category);
    if (!category?.serverId || category.fixed) throw new Error("등록 가능한 일정 분류를 선택해 주세요.");
    const normalized = normalizeEventTimes(draft);
    const payload = { title: draft.title.trim(), startDate: normalized.date, endDate: normalized.endDate,
      startTime: normalized.startTime, endTime: normalized.endTime, categoryId: category.serverId, description: draft.description.trim() };
    const saved = id ? await updateCalendarEvent(id, payload) : await createCalendarEvent(payload);
    setEvents((current) => [...current.filter((event) => event.id !== saved.id), saved]);
    return saved;
  };
  const removeEvent = async (id) => {
    await deleteCalendarEvent(id);
    setEvents((current) => current.filter((event) => event.id !== id));
  };
  const saveCategories = async (draft, deletedIds = []) => {
    // Persist each created ID in the caller's draft so a retry cannot create duplicates.
    const next = draft.map((item) => ({ ...item }));
    try {
      for (let index = 0; index < next.length; index++) {
        if (!next[index].serverId) {
          const created = await createCalendarCategory({ label: next[index].label, color: next[index].color });
          next[index] = { ...created, enabled: next[index].enabled !== false };
          Object.assign(draft[index], next[index]);
        }
      }
      // Omission means unchanged. Only an explicit, owned custom-category removal sends DELETE.
      const removed = new Set(deletedIds);
      const kept = new Set(next.map((item) => item.serverId));
      for (const id of removed) {
        const category = categorySettings.find((item) => item.serverId === id);
        // A previous attempt may have deleted it before a later PUT failed and refreshed state.
        if (!category) continue;
        if (category.fixed || !category.id.startsWith("custom-") || kept.has(id)) {
          throw new Error("삭제할 사용자 카테고리를 확인해 주세요.");
        }
        await deleteCalendarCategory(id);
      }
      for (const category of categorySettings) {
        if (!kept.has(category.serverId) && !removed.has(category.serverId)) next.push({ ...category });
      }
      const saved = await saveCalendarCategories(next);
      setCategories(saved);
      return saved;
    } catch (error) {
      // Refresh after partial success; already created categories keep their server IDs.
      try { setCategories(await getCalendarCategories()); } catch {}
      throw error;
    }
  };
  const value = { events, categorySettings, saveEvent, removeEvent, saveCategories,
    loading, error, retry, requestRange, authenticated: Boolean(userId) };
  return <CalendarDataContext.Provider value={value}>{children}</CalendarDataContext.Provider>;
}

export function useCalendarData(from, to) {
  const value = useContext(CalendarDataContext);
  const requestRange = value?.requestRange;
  useEffect(() => { if (from && to) requestRange?.(from, to); }, [from, to, requestRange]);
  if (!value) throw new Error("useCalendarData requires CalendarDataProvider");
  return value;
}
