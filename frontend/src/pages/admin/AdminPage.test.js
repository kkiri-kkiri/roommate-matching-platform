import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import "@testing-library/jest-dom";
import AdminPage from "./AdminPage";
import * as api from "../../api/adminApi";

let mockInitialParams;
let mockSetParams;
jest.mock("react-router-dom", () => ({
  useSearchParams: () => {
    const [params, setParams] = require("react").useState(() => new URLSearchParams(mockInitialParams));
    mockSetParams = (next) => setParams(new URLSearchParams(next));
    return [params, mockSetParams];
  },
  Link: ({ to, children, ...props }) => <a href={to} {...props} onClick={(event) => { event.preventDefault(); mockSetParams(to.split("?")[1]); }}>{children}</a>,
}));
jest.mock("../../api/adminApi");

const member = { id: 1, nickname: "서버 회원", schoolEmail: "server@school.ac.kr", dormitory: "나래", major: null, createdAt: "2026-09-01", status: "ACTIVE" };
const memberDetail = { ...member, gender: null, faculty: null, admissionYear: "24", grade: "3학년", mbti: null, activity: { postCount: 25, commentCount: 37, reportsReceived: 9 } };
const author = { id: 99, nickname: "목록 밖 작성자" };
const post = { id: 2, category: "자유게시판", title: "서버 게시글", author, createdAt: "2026-09-21T10:00:00", views: 123, likes: 45, commentCount: 67, reported: true };
const comment = { id: 3, content: "서버 댓글", author, createdAt: post.createdAt, postId: 2, postTitle: post.title, reported: true };
const report = { id: 4, type: "POST", targetContent: "신고 대상", reporter: author, targetUser: { id: 1, nickname: member.nickname }, reason: "광고", createdAt: post.createdAt, status: "PENDING" };
const reportDetail = { ...report, targetId: 2, targetUser: member, reporter: { ...author, schoolEmail: "reporter@school.ac.kr" }, description: "상세 신고 사유" };
const schedule = { id: 5, title: "서버 일정", date: "2026-09-25", content: "수집한 내용", category: "SCHOOL", source: "학교", collectedAt: post.createdAt };
const page = (content, overrides = {}) => ({ content, page: 0, size: 6, totalElements: content.length, totalPages: content.length ? 1 : 0, first: true, last: true, ...overrides });
const dashboard = { stats: { totalMembers: 101, totalPosts: 202, totalComments: 303, totalReports: 404, pendingReports: 5, schoolSchedules: 606, dormitorySchedules: 707 }, recentMembers: [{ id: 1, nickname: member.nickname, major: null, dormitory: member.dormitory, createdAt: member.createdAt, status: member.status }], recentPosts: [{ id: 2, title: post.title, category: post.category, author, createdAt: post.createdAt }], recentReports: [{ id: 4, status: report.status, type: report.type, reason: report.reason, createdAt: report.createdAt }], recentSchoolSchedules: [{ id: 5, title: schedule.title, date: schedule.date, collectedAt: schedule.collectedAt }], recentDormitorySchedules: [] };

beforeEach(() => {
  jest.resetAllMocks();
  mockInitialParams = { view: "members" };
  HTMLDialogElement.prototype.showModal = jest.fn(function () { this.open = true; });
  HTMLDialogElement.prototype.close = jest.fn(function () { this.open = false; });
  api.getAdminDashboard.mockResolvedValue(dashboard);
  api.getAdminMembers.mockResolvedValue(page([member]));
  api.getAdminMember.mockResolvedValue(memberDetail);
  api.getAdminPosts.mockResolvedValue(page([post]));
  api.getAdminPost.mockResolvedValue({ ...post, content: "서버 본문" });
  api.getAdminComments.mockResolvedValue(page([comment]));
  api.getAdminComment.mockResolvedValue(comment);
  api.getAdminReports.mockResolvedValue(page([report]));
  api.getAdminReport.mockResolvedValue(reportDetail);
  api.getAdminCrawledSchedules.mockResolvedValue(page([schedule]));
  api.getAdminCrawledSchedule.mockResolvedValue(schedule);
  api.getAdminScheduleCrawlStatuses.mockResolvedValue([{ category: "SCHOOL", status: null, lastRunAt: null, recentCollectedCount: 0, totalCollectedCount: 1, lastCompletedAt: null, lastFailure: null }]);
});
const dialog = () => within(screen.getByRole("dialog"));
async function confirm(name) {
  fireEvent.click(await dialog().findByRole("button", { name, exact: true }));
  await act(async () => {
    fireEvent.click(dialog().getByRole("button", { name: name === "회원 탈퇴 처리" ? "회원 탈퇴" : name, exact: true }));
  });
}

test("renders dashboard stats and summary DTOs; fetches full detail separately", async () => {
  mockInitialParams = { view: "dashboard" };
  render(<AdminPage />);
  expect(screen.getByText("대시보드를 불러오는 중입니다.")).toBeInTheDocument();
  await screen.findByText("101");
  expect(screen.getByText("707")).toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "서버 회원" }));
  await dialog().findByText("입학 연도");
  expect(api.getAdminMember).toHaveBeenCalledWith(1);
  expect(dialog().getByText("25")).toBeInTheDocument();
  expect(api.getAdminMembers).not.toHaveBeenCalled();
});

test("filters and searches on the server, resets page, and uses server totals", async () => {
  mockInitialParams = { view: "members", page: "2" };
  api.getAdminMembers.mockImplementation(async (params) => page([member], { page: params.page, totalElements: 13, totalPages: 3, first: params.page === 0, last: params.page === 2 }));
  render(<AdminPage />);
  await screen.findByText("총 13건");
  expect(api.getAdminMembers).toHaveBeenLastCalledWith({ page: 1, size: 6, q: undefined, status: undefined, dormitory: undefined });
  fireEvent.click(screen.getByRole("button", { name: "다음 페이지" }));
  await waitFor(() => expect(api.getAdminMembers).toHaveBeenLastCalledWith(expect.objectContaining({ page: 2 })));
  await screen.findByText("총 13건");
  fireEvent.change(screen.getByLabelText("회원 상태"), { target: { value: "이용 정지" } });
  await waitFor(() => expect(api.getAdminMembers).toHaveBeenLastCalledWith(expect.objectContaining({ page: 0, status: "SUSPENDED" })));
  fireEvent.change(screen.getByLabelText("생활관"), { target: { value: "나래관" } });
  fireEvent.change(screen.getByRole("searchbox"), { target: { value: "검색어" } });
  fireEvent.click(screen.getByRole("button", { name: "검색", exact: true }));
  await waitFor(() => expect(api.getAdminMembers).toHaveBeenLastCalledWith({ page: 0, size: 6, q: "검색어", status: "SUSPENDED", dormitory: "나래" }));
  expect(await screen.findByRole("button", { name: member.schoolEmail })).toBeInTheDocument();
});

test.each([["이용 정지", "SUSPENDED"], ["회원 탈퇴 처리", "WITHDRAWN"]])("changes member status via %s and refetches detail/list", async (button, status) => {
  render(<AdminPage />);
  fireEvent.click(await screen.findByRole("button", { name: member.schoolEmail }));
  await dialog().findByText("입학 연도");
  api.getAdminMember.mockResolvedValue({ ...memberDetail, status });
  await confirm(button);
  await waitFor(() => expect(api.updateAdminMemberStatus).toHaveBeenCalledWith(1, status));
  await waitFor(() => expect(api.getAdminMembers).toHaveBeenCalledTimes(2));
  expect(api.getAdminMember).toHaveBeenCalledTimes(2);
});

test("shows server status-transition errors, including withdrawn reactivation", async () => {
  api.getAdminMember.mockResolvedValue({ ...memberDetail, status: "WITHDRAWN" });
  api.updateAdminMemberStatus.mockRejectedValue(new Error("탈퇴한 회원은 변경할 수 없습니다."));
  render(<AdminPage />);
  fireEvent.click(await screen.findByRole("button", { name: member.schoolEmail }));
  await dialog().findByText("입학 연도");
  await confirm("이용 정지 해제");
  expect(api.updateAdminMemberStatus).toHaveBeenCalledWith(1, "ACTIVE");
  expect(await dialog().findByRole("alert")).toHaveTextContent("탈퇴한 회원은 변경할 수 없습니다.");
  expect(api.getAdminMembers).toHaveBeenCalledTimes(1);
});

test.each([[false, "getAdminPosts", "deleteAdminPost", "게시글 삭제", 2], [true, "getAdminComments", "deleteAdminComment", "댓글 삭제", 3]])("deletes community content and refetches (comments=%s)", async (comments, list, mutation, button, id) => {
  mockInitialParams = { view: "community", ...(comments ? { tab: "comments" } : {}) };
  render(<AdminPage />);
  fireEvent.click(await screen.findByRole("button", { name: "상세 보기" }));
  await dialog().findByRole("button", { name: button });
  api[list].mockResolvedValue(page([]));
  await confirm(button);
  await waitFor(() => expect(api[mutation]).toHaveBeenCalledWith(id));
  await screen.findByText("표시할 내역이 없습니다");
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  expect(api[list]).toHaveBeenCalledTimes(2);
});

test.each([["회원 이용 정지", "SUSPEND_USER"], ["게시글 삭제", "DELETE_TARGET"], ["신고 반려", "REJECT"], ["처리 완료", "COMPLETE"]])("sends report action %s", async (button, action) => {
  mockInitialParams = { view: "reports", status: "처리 대기", type: "게시글" };
  render(<AdminPage />);
  fireEvent.click(await screen.findByRole("button", { name: "#4" }));
  await dialog().findByText("상세 신고 사유");
  expect(api.getAdminReports).toHaveBeenCalledWith(expect.objectContaining({ status: "PENDING", type: "POST" }));
  expect(dialog().getByText(/reporter@school.ac.kr/)).toBeInTheDocument();
  await confirm(button);
  await waitFor(() => expect(api.processAdminReport).toHaveBeenCalledWith(4, action));
  await waitFor(() => expect(api.getAdminReports).toHaveBeenCalledTimes(2));
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
});

test("uses schedule date as returned, supports null crawl history and read-only detail", async () => {
  mockInitialParams = { view: "schedules", category: "학교 일정" };
  render(<AdminPage />);
  await screen.findByText("실행 이력 없음");
  fireEvent.click(await screen.findByRole("button", { name: "서버 일정" }));
  await dialog().findByText("2026-09-25");
  expect(api.getAdminCrawledSchedules).toHaveBeenCalledWith(expect.objectContaining({ category: "SCHOOL" }));
  expect(api.getAdminCrawledSchedule).toHaveBeenCalledWith(5);
  expect(dialog().queryByRole("button", { name: /삭제|수정|등록/ })).not.toBeInTheDocument();
});

test("renders request failure without mock fallback and allows retry", async () => {
  api.getAdminMembers.mockRejectedValueOnce(new Error("관리자 권한이 없습니다."));
  render(<AdminPage />);
  expect(screen.getByText("목록을 불러오는 중입니다.")).toBeInTheDocument();
  expect(await screen.findByRole("alert")).toHaveTextContent("관리자 권한이 없습니다.");
  expect(screen.queryByRole("table")).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));
  await screen.findByRole("button", { name: member.schoolEmail });
});

test("ignores late list results after navigation, including returning to dashboard", async () => {
  let resolve;
  api.getAdminMembers.mockReturnValue(new Promise((done) => { resolve = done; }));
  render(<AdminPage />);
  await waitFor(() => expect(api.getAdminMembers).toHaveBeenCalled());
  fireEvent.click(screen.getByRole("link", { name: "대시보드" }));
  await screen.findByText("101");
  await act(async () => resolve(page([member])));
  expect(screen.getByText("101")).toBeInTheDocument();
  expect(screen.queryByRole("button", { name: member.schoolEmail })).not.toBeInTheDocument();
});

test("sends post sorting, category and reported filters without filtering returned rows locally", async () => {
  mockInitialParams = { view: "community", sort: "좋아요순", category: "자유게시판", reported: "true", q: "검색어" };
  render(<AdminPage />);
  await screen.findByText("서버 게시글");
  expect(api.getAdminPosts).toHaveBeenCalledWith({ page: 0, size: 6, q: "검색어", category: "자유게시판", reported: true, sort: "LIKES" });
  expect(screen.getByText("67")).toBeInTheDocument();
  fireEvent.change(screen.getByLabelText("정렬"), { target: { value: "조회순" } });
  await waitFor(() => expect(api.getAdminPosts).toHaveBeenLastCalledWith(expect.objectContaining({ sort: "VIEWS", page: 0 })));
  await screen.findByText("서버 게시글");
});

test("fetches a report target outside the current list and shows detail errors", async () => {
  mockInitialParams = { view: "reports" };
  api.getAdminPost.mockRejectedValue(new Error("삭제된 게시글입니다."));
  render(<AdminPage />);
  fireEvent.click(await screen.findByRole("button", { name: "#4" }));
  fireEvent.click(await dialog().findByRole("button", { name: "신고 대상 상세 확인" }));
  expect(await dialog().findByRole("alert")).toHaveTextContent("삭제된 게시글입니다.");
  expect(api.getAdminPost).toHaveBeenCalledWith(2);
});

test("keeps failed deletion in the dialog and prevents duplicate submissions", async () => {
  mockInitialParams = { view: "community" };
  let reject;
  api.deleteAdminPost.mockReturnValue(new Promise((resolve, fail) => { reject = fail; }));
  render(<AdminPage />);
  fireEvent.click(await screen.findByRole("button", { name: "상세 보기" }));
  await confirm("게시글 삭제");
  const pending = dialog().getByRole("button", { name: "처리 중…" });
  expect(pending).toBeDisabled();
  fireEvent.click(pending);
  expect(api.deleteAdminPost).toHaveBeenCalledTimes(1);
  await act(async () => reject(new Error("삭제 실패")));
  expect(dialog().getByRole("alert")).toHaveTextContent("삭제 실패");
  expect(api.getAdminPosts).toHaveBeenCalledTimes(1);
});

test("returns to the last available page after deleting its only row", async () => {
  mockInitialParams = { view: "community", page: "2" };
  api.getAdminPosts.mockResolvedValueOnce(page([post], { page: 1, totalElements: 7, totalPages: 2, first: false }))
    .mockResolvedValueOnce(page([], { page: 1, totalElements: 6, totalPages: 1, first: false }))
    .mockResolvedValue(page([post], { totalElements: 6 }));
  render(<AdminPage />);
  fireEvent.click(await screen.findByRole("button", { name: "상세 보기" }));
  await confirm("게시글 삭제");
  await waitFor(() => expect(api.getAdminPosts).toHaveBeenLastCalledWith(expect.objectContaining({ page: 0 })));
  await screen.findByText("서버 게시글");
});
