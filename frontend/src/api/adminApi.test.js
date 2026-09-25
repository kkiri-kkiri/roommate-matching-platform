import axios from "axios";
import * as api from "./adminApi";

jest.mock("axios", () => ({ create: jest.fn(() => ({ request: jest.fn() })) }));
const request = axios.create.mock.results[0].value.request;
beforeEach(() => {
  request.mockReset().mockResolvedValue({ data: { data: { result: "server" } } });
  window.localStorage.setItem("accessToken", "admin-token");
});

test.each([
  ["getAdminDashboard", [], "get", "/admin/dashboard"],
  ["getAdminMember", [12], "get", "/admin/members/12"],
  ["updateAdminMemberStatus", [12, "SUSPENDED"], "patch", "/admin/members/12/status", { status: "SUSPENDED" }],
  ["getAdminPost", [12], "get", "/admin/posts/12"],
  ["deleteAdminPost", [12], "delete", "/admin/posts/12"],
  ["getAdminComment", [12], "get", "/admin/comments/12"],
  ["deleteAdminComment", [12], "delete", "/admin/comments/12"],
  ["getAdminReport", [12], "get", "/admin/reports/12"],
  ["processAdminReport", [12, "DELETE_TARGET"], "patch", "/admin/reports/12", { action: "DELETE_TARGET" }],
  ["getAdminScheduleCrawlStatuses", [], "get", "/admin/schedule-crawls/status"],
  ["getAdminCrawledSchedule", [12], "get", "/admin/crawled-schedules/12"],
])("%s sends authenticated method, URL and body and unwraps data", async (...entry) => {
  const [name, args, method, url, data] = entry;
  await expect(api[name](...args)).resolves.toEqual({ result: "server" });
  expect(request).toHaveBeenCalledWith({ method, url, ...(data ? { data } : {}), headers: { Authorization: "Bearer admin-token" } });
});

test.each([
  ["getAdminMembers", "members", { status: "ACTIVE", dormitory: "나래관" }],
  ["getAdminPosts", "posts", { category: "자유게시판", reported: false, sort: "VIEWS" }],
  ["getAdminComments", "comments", { reported: true }],
  ["getAdminReports", "reports", { type: "COMMENT", status: "PENDING" }],
  ["getAdminCrawledSchedules", "crawled-schedules", { category: "SCHOOL" }],
])("%s sends only its DTO query fields", async (name, path, filters) => {
  const params = { page: 2, size: 6, q: "검색", ...filters };
  await api[name]({ ...params, unsupported: "ignored" });
  expect(request).toHaveBeenCalledWith({ method: "get", url: `/admin/${path}`, params, headers: { Authorization: "Bearer admin-token" } });
});

test.each([401, 403, 400])("preserves server message for HTTP %s", async (status) => {
  request.mockRejectedValue({ response: { status, data: { message: "서버 오류 설명" } } });
  await expect(api.getAdminDashboard()).rejects.toThrow("서버 오류 설명");
});
test.each([[401, "다시 로그인"], [403, "관리자 권한이 없습니다"]])("explains HTTP %s", async (status, hint) => {
  request.mockRejectedValue({ response: { status, data: {} } });
  await expect(api.getAdminDashboard()).rejects.toThrow(hint);
});
test("requires a token and does not send anonymous requests", async () => {
  window.localStorage.removeItem("accessToken");
  await expect(api.getAdminDashboard()).rejects.toThrow("로그인 후 이용해주세요.");
  expect(request).not.toHaveBeenCalled();
});
