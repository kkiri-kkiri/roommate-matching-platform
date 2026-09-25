import { useEffect, useState } from "react";
import * as api from "../../api/adminApi";

export const LABELS = {
  ACTIVE: "정상", SUSPENDED: "이용 정지", WITHDRAWN: "탈퇴",
  PENDING: "처리 대기", COMPLETED: "처리 완료", REJECTED: "반려",
  USER: "사용자", POST: "게시글", COMMENT: "댓글", SCHOOL: "학교 일정", DORMITORY: "기숙사 일정",
};
const CODES = Object.fromEntries(Object.entries(LABELS).map(([code, label]) => [label, code]));
const code = (value) => CODES[value] || value || undefined;
// Reference data stores the names without 관; retain the existing filter labels.
const DORMITORIES = { 나래관: "나래", 호연관: "호연", 창조관: "창조", 비봉관: "비봉" };
export const label = (value) => LABELS[value] || value || "-";
const details = {
  member: api.getAdminMember, post: api.getAdminPost, comment: api.getAdminComment,
  report: api.getAdminReport, schedule: api.getAdminCrawledSchedule,
};

// Each request owns its result; responses from a previous filter or dialog are ignored.
function useRequest(fetcher, dependencies) {
  const key = JSON.stringify(dependencies);
  const [state, setState] = useState({ key, data: null, loading: true, error: "" });
  useEffect(() => {
    let active = true;
    setState({ key, data: null, loading: true, error: "" });
    Promise.resolve().then(fetcher).then(
      (data) => { if (active) setState({ key, data, loading: false, error: "" }); },
      (error) => { if (active) setState({ key, data: null, loading: false, error: error.message || "조회에 실패했습니다." }); },
    );
    return () => { active = false; };
    // The caller supplies all scalar inputs to its request.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, dependencies);
  return state.key === key ? state : { data: null, loading: true, error: "" };
}

export default function useAdminData({ view, isComments, query, status, category, dorm, type, reportedOnly, sort, page, revision, modal }) {
  const main = useRequest(() => {
    const common = { page: page - 1, size: 6, q: query || undefined };
    if (view === "dashboard") return api.getAdminDashboard();
    if (view === "members") return api.getAdminMembers({ ...common, status: code(status), dormitory: DORMITORIES[dorm] || dorm || undefined });
    if (view === "community") {
      const params = { ...common, reported: reportedOnly ? true : undefined };
      return isComments ? api.getAdminComments(params) : api.getAdminPosts({ ...params, category: category || undefined, sort: { 최신순: "LATEST", 조회순: "VIEWS", 좋아요순: "LIKES" }[sort] || sort });
    }
    if (view === "reports") return api.getAdminReports({ ...common, status: code(status), type: code(type) });
    return api.getAdminCrawledSchedules({ ...common, category: code(category) });
  }, [view, isComments, query, status, category, dorm, type, reportedOnly, sort, page, revision]);
  const crawl = useRequest(() => view === "schedules" ? api.getAdminScheduleCrawlStatuses() : null, [view, revision]);
  const detail = useRequest(() => modal ? details[modal.kind](modal.id) : null, [modal?.kind, modal?.id, revision]);
  return { main, crawl, detail };
}
