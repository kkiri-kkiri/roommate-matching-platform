import { createContext, useContext, useEffect, useMemo, useState } from "react";
import * as S from "./CalendarPageCss";

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

export function dateKey(date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

function createSampleEvents(today) {
  const sampleDate = (day, offset = 0) => dateKey(new Date(today.getFullYear(), today.getMonth() + offset, day));
  return [
    { id: "sample-1", title: "학기 일정 확인", date: sampleDate(2), startTime: "09:00", endTime: "10:00", category: "school", description: "이번 학기 수업과 학사 일정을 확인해요." },
    { id: "sample-2", title: "생활관 정기 점검", date: sampleDate(8), startTime: "18:00", endTime: "19:00", category: "dormitory", description: "공용 공간과 생활관 시설을 함께 점검해요.\n점검 전에 개인 물품을 정리해 주세요." },
    { id: "sample-3", title: "룸메이트와 첫 만남", date: dateKey(today), startTime: "12:00", endTime: "13:00", category: "roommate", description: "학생회관 1층에서 만나 함께 점심을 먹어요.\n서로의 생활 패턴과 공용 물품에 대해 이야기해요." },
    { id: "sample-4", title: "도서관에서 과제하기", date: dateKey(today), startTime: "15:00", endTime: "17:00", category: "personal", description: "중앙도서관에서 이번 주 과제를 정리해요." },
    { id: "sample-5", title: "학과 진로 특강", date: sampleDate(16), startTime: "14:00", endTime: "16:00", category: "school", description: "전공과 관련된 진로를 알아보는 시간이에요. 필기 도구를 준비해요." },
    { id: "sample-6", title: "룸메이트 생활 규칙 정하기", date: sampleDate(21), startTime: "19:00", endTime: "20:00", category: "roommate", description: "청소 순서와 취침 시간, 공용 물품 사용 규칙을 함께 정해요." },
    { id: "sample-7", title: "생활관 소방 대피 훈련", date: sampleDate(24), startTime: "18:30", endTime: "19:00", category: "dormitory", description: "생활관 앞 지정 장소에 모여 대피 경로를 확인해요." },
    { id: "sample-8", title: "한 달 생활비 정리", date: sampleDate(27), startTime: "20:00", endTime: "20:30", category: "personal", description: "이번 달 지출을 돌아보고 다음 달 예산을 정해요." },
    { id: "sample-9", title: "생활관 입사 준비", date: sampleDate(26, -1), startTime: "10:00", endTime: "11:00", category: "dormitory", description: "입사에 필요한 물품과 준비 서류를 확인해요." },
    { id: "sample-10", title: "다음 달 학습 계획", date: sampleDate(3, 1), startTime: "16:00", endTime: "17:00", category: "personal", description: "수업별 학습 목표를 세우고 달력에 정리해요." },
  ];
}

const CalendarDataContext = createContext(null);

export function CalendarDataProvider({ children }) {
  const [categorySettings, setCategorySettings] = useState(loadCategorySettings);
  // Keep the calendar's frontend data alive across route changes.
  const [events, setEvents] = useState(() => createSampleEvents(new Date()).map((event) => (
    categorySettings.some(({ id }) => id === event.category) ? event : { ...event, category: "school" }
  )));

  useEffect(() => {
    try { localStorage.setItem(CATEGORY_SETTINGS_KEY, JSON.stringify(categorySettings)); } catch {}
  }, [categorySettings]);

  const value = useMemo(() => ({ events, setEvents, categorySettings, setCategorySettings }),
    [events, categorySettings]);

  return <CalendarDataContext.Provider value={value}>{children}</CalendarDataContext.Provider>;
}

export function useCalendarData() {
  const value = useContext(CalendarDataContext);
  if (!value) throw new Error("useCalendarData requires CalendarDataProvider");
  return value;
}
