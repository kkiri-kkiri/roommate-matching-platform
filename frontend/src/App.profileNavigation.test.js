import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import "@testing-library/jest-dom";
import { MemoryRouter } from "react-router-dom";
import App from "./App";
import * as roommateApi from "./api/roommateApi";
import { getCommunityPosts } from "./api/communityApi";

jest.mock("react-router-dom", () => {
  global.TextEncoder = require("util").TextEncoder;
  global.TextDecoder = require("util").TextDecoder;
  return jest.requireActual("react-router-dom");
});
jest.mock("./api/roommateApi");
jest.mock("./api/communityApi");
jest.mock("./layouts/MainLayout", () => () => {
  const { Outlet } = require("react-router-dom");
  return <Outlet />;
});
jest.mock("./calendar/CalendarDataContext", () => ({
  CalendarDataProvider: ({ children }) => children,
  dateKey: (date) => date.toISOString().slice(0, 10),
  useCalendarData: () => ({ events: [], categorySettings: [], loading: false, error: "" }),
}));
jest.mock("./pages/auth/LoginPage", () => ({ onLogin }) => {
  const navigate = require("react-router-dom").useNavigate();
  return <button onClick={() => {
    onLogin({ id: 1, displayName: "로그인 사용자" });
    navigate("/");
  }}>테스트 로그인</button>;
});
jest.mock("./pages/roommate/RoommateSearchPage", () => () => <h1>룸메찾기 페이지</h1>);
jest.mock("./pages/mypage/MyPage", () => ({
  __esModule: true,
  default: () => null,
  getSavedSurveyProfiles: () => [],
}));
jest.mock("./pages/chat/ChatPage", () => () => null);
jest.mock("./pages/admin/AdminPage", () => () => null);
jest.mock("./calendar/CalendarPage", () => () => null);
jest.mock("./pages/community/CommunityPage", () => () => null);
jest.mock("./pages/auth/FindPasswordPage", () => () => null);
jest.mock("./pages/auth/SignupPage", () => () => null);

const profile = {
  id: 42, nickname: "추천 룸메이트", major: "소프트웨어융합전공",
  admissionYear: "24", introduction: "메인 페이지 추천 소개", matchScore: 94,
};

beforeEach(() => {
  jest.clearAllMocks();
  HTMLDialogElement.prototype.showModal = jest.fn(function () { this.open = true; });
  HTMLDialogElement.prototype.close = jest.fn(function () { this.open = false; });
  roommateApi.getRecommendedRoommates.mockResolvedValue({ content: [profile] });
  roommateApi.getRoommateDetail.mockResolvedValue({ ...profile, answers: { sleepTime: "23시" } });
  getCommunityPosts.mockResolvedValue({ content: [] });
});

async function renderMainPage() {
  render(<MemoryRouter initialEntries={["/login"]}><App /></MemoryRouter>);
  fireEvent.click(await screen.findByRole("button", { name: "테스트 로그인" }));
  return screen.findByRole("link", { name: "추천 룸메이트 상세 프로필 보기" });
}

test.each(["profile link", "card body"])("opens %s over the main page without mounting roommate search", async (target) => {
  const link = await renderMainPage();
  fireEvent.click(target === "profile link" ? link : screen.getByText(profile.introduction));

  const dialog = await screen.findByRole("dialog");
  expect(await within(dialog).findByRole("heading", { name: profile.nickname })).toBeInTheDocument();
  expect(screen.getByRole("heading", { name: "나와 잘 맞는 룸메이트" })).toBeInTheDocument();
  expect(screen.queryByRole("heading", { name: "룸메찾기 페이지" })).not.toBeInTheDocument();
  expect(roommateApi.getRoommateDetail).toHaveBeenCalledWith("42", expect.any(AbortSignal));
  expect(roommateApi.getRecommendedRoommates).toHaveBeenCalledTimes(1);
});

test.each(["close button", "escape"])("returns to the preserved main page with %s", async (action) => {
  const link = await renderMainPage();
  link.focus();
  fireEvent.click(link);
  const dialog = await screen.findByRole("dialog");
  await within(dialog).findByRole("heading", { name: profile.nickname });

  if (action === "close button") fireEvent.click(within(dialog).getByRole("button", { name: "상세 프로필 닫기" }));
  else fireEvent(dialog, new Event("cancel", { bubbles: false, cancelable: true }));

  await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
  expect(screen.getByRole("link", { name: "추천 룸메이트 상세 프로필 보기" })).toBe(link);
  expect(roommateApi.getRecommendedRoommates).toHaveBeenCalledTimes(1);
  expect(screen.queryByRole("heading", { name: "룸메찾기 페이지" })).not.toBeInTheDocument();
});

test("the main page more link still opens roommate search", async () => {
  await renderMainPage();
  fireEvent.click(screen.getByRole("link", { name: "더보기" }));
  expect(await screen.findByRole("heading", { name: "룸메찾기 페이지" })).toBeInTheDocument();
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
});

test("a direct profile URL still shows the roommate search page under the dialog", async () => {
  render(<MemoryRouter initialEntries={["/roommates/42?tab=recommended"]}><App /></MemoryRouter>);
  const dialog = await screen.findByRole("dialog");
  expect(await within(dialog).findByRole("heading", { name: profile.nickname })).toBeInTheDocument();
  expect(screen.getByRole("heading", { name: "룸메찾기 페이지" })).toBeInTheDocument();
  fireEvent.click(within(dialog).getByRole("button", { name: "상세 프로필 닫기" }));
  await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
  expect(screen.getByRole("heading", { name: "룸메찾기 페이지" })).toBeInTheDocument();
});
