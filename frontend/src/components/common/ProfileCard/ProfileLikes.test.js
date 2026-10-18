import { StrictMode } from "react";
import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import "@testing-library/jest-dom";
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
import ProfileCard from "./ProfileCard";
import DetailedProfile from "./DetailedProfile";
import MyPage from "../../../pages/mypage/MyPage";
import RoommateSearchPage from "../../../pages/roommate/RoommateSearchPage";
import * as api from "../../../api/roommateApi";
import * as userApi from "../../../api/userApi";
import * as surveyApi from "../../../api/surveyApi";
import { clearStoredAuthentication, getAuthenticationSession, storeAccessToken } from "../../../auth/session";

jest.mock("react-router-dom", () => {
  global.TextEncoder = require("util").TextEncoder;
  global.TextDecoder = require("util").TextDecoder;
  return jest.requireActual("react-router-dom");
});

jest.mock("../../../api/roommateApi", () => ({
  ...jest.requireActual("../../../api/roommateApi"),
  getMyLikedRoommates: jest.fn(), getRoommateDetail: jest.fn(), setRoommateLike: jest.fn(),
  getRoommates: jest.fn(), getFilteredRecommendedRoommates: jest.fn(),
}));
jest.mock("../../../api/userApi");
jest.mock("../../../api/surveyApi");

const account = { id: 1, nickname: "나", faculty: "HK자율전공학부", dormitory: "나래", admissionYear: "24" };
const target = (id = 20, liked = false) => ({ id, nickname: `대상${id}`, liked,
  userId: id, source: "backend", tags: [], answers: {}, matchScore: null });
const jwt = (userId) => `test.${btoa(JSON.stringify({ userId, exp: Math.floor(Date.now() / 1000) + 3600 }))}.signature`;
const deferred = () => { let resolve, reject; const promise = new Promise((ok, fail) => { resolve = ok; reject = fail; }); return { promise, resolve, reject }; };
let saved;
const ownedLikes = () => {
  const id = getAuthenticationSession().userId;
  if (!saved.has(id)) saved.set(id, new Set());
  return saved.get(id);
};
function serverPage(page = 0, size = 6) {
  const ids = [...ownedLikes()].sort((a, b) => a - b);
  const totalPages = Math.ceil(ids.length / size);
  return { content: ids.slice(page * size, (page + 1) * size).map(id => target(id, true)),
    page, size, totalElements: ids.length, totalPages, first: page === 0, last: page >= totalPages - 1 };
}
function mount(children, path = "/profile?tab=likes") {
  return render(<MemoryRouter initialEntries={[path]}>{children}</MemoryRouter>);
}
const card = (liked = false, id = 20) => <ProfileCard profile={target(id, liked)} activeTab="all" />;
const detail = () => <DetailedProfile profileId={20} onDismiss={() => {}} />;

function MyPageRoutes() {
  const location = useLocation();
  const background = location.state?.backgroundLocation;
  return <>
    <Routes location={background || location}>
      <Route path="/profile" element={<MyPage currentUser={account} />} />
      <Route path="/roommates/:profileId" element={<DetailedProfile />} />
    </Routes>
    {background && <Routes><Route path="/roommates/:profileId" element={<DetailedProfile />} /></Routes>}
  </>;
}

beforeEach(() => {
  jest.clearAllMocks();
  localStorage.clear();
  storeAccessToken(jwt(1));
  saved = new Map([[1, new Set()], [2, new Set()]]);
  window.scrollTo = jest.fn();
  window.alert = jest.fn();
  HTMLDialogElement.prototype.showModal = function () { this.open = true; };
  HTMLDialogElement.prototype.close = function () { this.open = false; };
  userApi.getMyInfo.mockImplementation(async () => ({ ...account, id: getAuthenticationSession().userId }));
  surveyApi.getMyPreferences.mockResolvedValue({ completed: false, answers: {} });
  surveyApi.getMyProfileSurvey.mockResolvedValue({ completed: false, answers: {} });
  api.getMyLikedRoommates.mockImplementation(async ({ page, size }) => serverPage(page, size));
  api.getRoommateDetail.mockImplementation(async id => target(Number(id), ownedLikes().has(Number(id))));
  api.getRoommates.mockImplementation(async () => ({ ...serverPage(0, 9), content: [target(20, ownedLikes().has(20))] }));
  api.getFilteredRecommendedRoommates.mockImplementation(api.getRoommates.getMockImplementation());
  api.setRoommateLike.mockImplementation(async (id, liked) => { if (liked) ownedLikes().add(id); else ownedLikes().delete(id); });
});

test("card registration refreshes the mounted MyPage list and total exactly once", async () => {
  mount(<>{card()}<MyPage currentUser={account} /></>);
  await screen.findByText("아직 마음에 담은 룸메이트가 없어요");
  fireEvent.click(screen.getByRole("button", { name: "대상20 프로필 좋아요" }));
  await screen.findByText("1명");
  expect(screen.getAllByRole("button", { name: "대상20 프로필 좋아요 취소" })).toHaveLength(2);
  expect(api.setRoommateLike).toHaveBeenCalledTimes(1);
  expect(api.getMyLikedRoommates).toHaveBeenCalledTimes(2);
});

test("detail cancellation refreshes MyPage content and total and synchronizes both hearts", async () => {
  ownedLikes().add(20); ownedLikes().add(21);
  mount(<><MyPage currentUser={account} />{detail()}</>);
  await screen.findByText("2명");
  fireEvent.click(await screen.findByRole("button", { name: "대상20님 좋아요 취소" }));
  await screen.findByText("1명");
  expect(screen.queryByRole("button", { name: /대상20 프로필 좋아요/ })).not.toBeInTheDocument();
  expect(screen.getByRole("button", { name: "대상20님 좋아요" })).toHaveAttribute("aria-pressed", "false");
  expect(screen.getByRole("button", { name: "대상21 프로필 좋아요 취소" })).toBeInTheDocument();
  expect(api.getMyLikedRoommates).toHaveBeenCalledTimes(2);
});

test("removing the only last-page profile moves back and loads content and total from server", async () => {
  for (let id = 20; id < 27; id++) ownedLikes().add(id);
  mount(<MyPage currentUser={account} />);
  await screen.findByText("7명");
  fireEvent.click(screen.getByRole("button", { name: "2페이지" }));
  fireEvent.click(await screen.findByRole("button", { name: "대상26 프로필 좋아요 취소" }));
  await screen.findByText("6명");
  expect(screen.getByRole("button", { name: "대상20 프로필 좋아요 취소" })).toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "대상26 프로필 좋아요 취소" })).not.toBeInTheDocument();
  expect(api.getMyLikedRoommates.mock.calls.map(([params]) => params.page)).toEqual([0, 1, 1, 0]);
});

test("detail cancellation of the final profile shows the empty state and zero total", async () => {
  ownedLikes().add(20);
  mount(<><MyPage currentUser={account} />{detail()}</>);
  await screen.findByText("1명");
  fireEvent.click(await screen.findByRole("button", { name: "대상20님 좋아요 취소" }));
  await screen.findByText("아직 마음에 담은 룸메이트가 없어요");
  expect(screen.getByText("0명")).toBeInTheDocument();
  expect(api.getMyLikedRoommates).toHaveBeenCalledTimes(2);
});

test("opening detail through the real card link and closing it preserves the refreshed background list", async () => {
  ownedLikes().add(20);
  mount(<MyPageRoutes />);
  await screen.findByText("1명");
  fireEvent.click(screen.getByRole("link", { name: "대상20 상세 프로필 보기" }));
  fireEvent.click(await screen.findByRole("button", { name: "대상20님 좋아요 취소" }));
  await screen.findByText("0명");
  fireEvent.click(screen.getByRole("button", { name: "상세 프로필 닫기" }));
  expect(await screen.findByText("아직 마음에 담은 룸메이트가 없어요")).toBeInTheDocument();
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  expect(api.getMyLikedRoommates).toHaveBeenCalledTimes(2);
});

test("one pending request disables both card and detail, blocks duplicate writes, then updates both hearts", async () => {
  const pending = deferred(); api.setRoommateLike.mockReturnValue(pending.promise);
  mount(<>{card()}{detail()}</>);
  const detailHeart = await screen.findByRole("button", { name: "대상20님 좋아요" });
  const cardHeart = screen.getByRole("button", { name: "대상20 프로필 좋아요" });
  fireEvent.click(cardHeart); fireEvent.click(cardHeart); fireEvent.click(detailHeart);
  expect(cardHeart).toBeDisabled(); expect(detailHeart).toBeDisabled();
  expect(api.setRoommateLike).toHaveBeenCalledTimes(1);
  await act(async () => pending.resolve());
  expect(cardHeart).toHaveAttribute("aria-pressed", "true");
  expect(detailHeart).toHaveAttribute("aria-pressed", "true");
  expect(cardHeart).toBeEnabled(); expect(detailHeart).toBeEnabled();
});

test("write failure preserves hearts, list and count; retry uses the common success path", async () => {
  ownedLikes().add(20);
  api.setRoommateLike.mockRejectedValueOnce(new Error("관심 취소 실패"));
  mount(<><MyPage currentUser={account} />{detail()}</>);
  await screen.findByText("1명");
  fireEvent.click(await screen.findByRole("button", { name: "대상20님 좋아요 취소" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("관심 취소 실패");
  expect(screen.getByRole("button", { name: "대상20 프로필 좋아요 취소" })).toHaveAttribute("aria-pressed", "true");
  expect(screen.getByText("1명")).toBeInTheDocument();
  expect(api.getMyLikedRoommates).toHaveBeenCalledTimes(1);
  fireEvent.click(screen.getByRole("button", { name: "대상20님 좋아요 취소" }));
  await screen.findByText("0명");
  expect(api.getMyLikedRoommates).toHaveBeenCalledTimes(2);
});

test.each(["success", "failure"])("late A %s cannot change B hearts, errors or pending request", async outcome => {
  const previous = deferred(), current = deferred();
  api.setRoommateLike.mockReturnValueOnce(previous.promise).mockReturnValueOnce(current.promise);
  const view = mount(card());
  fireEvent.click(screen.getByRole("button", { name: "대상20 프로필 좋아요" }));
  await act(async () => { clearStoredAuthentication(); storeAccessToken(jwt(2)); });
  view.rerender(<MemoryRouter>{card()}</MemoryRouter>);
  const heart = screen.getByRole("button", { name: "대상20 프로필 좋아요" });
  expect(heart).toBeEnabled();
  fireEvent.click(heart);
  expect(api.setRoommateLike).toHaveBeenCalledTimes(2);
  await act(async () => { if (outcome === "success") previous.resolve(); else previous.reject(new Error("A 실패")); });
  expect(heart).toHaveAttribute("aria-pressed", "false");
  expect(heart).toBeDisabled();
  expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  await act(async () => current.resolve());
  expect(heart).toHaveAttribute("aria-pressed", "true"); expect(heart).toBeEnabled();
});

test("same user relogin with the identical JWT invalidates the previous generation's result", async () => {
  const token = localStorage.getItem("accessToken");
  const previous = deferred(); api.setRoommateLike.mockReturnValue(previous.promise);
  mount(card());
  fireEvent.click(screen.getByRole("button", { name: "대상20 프로필 좋아요" }));
  const firstSession = getAuthenticationSession();
  await act(async () => { clearStoredAuthentication(); storeAccessToken(token); });
  expect(getAuthenticationSession().userId).toBe(firstSession.userId);
  expect(getAuthenticationSession().generation).not.toBe(firstSession.generation);
  await act(async () => previous.resolve());
  expect(screen.getByRole("button", { name: "대상20 프로필 좋아요" })).toHaveAttribute("aria-pressed", "false");
});

test.each([false, true])("an unmounted request cannot publish success into a newly mounted view (switch session: %s)", async switchSession => {
  const previous = deferred(); api.setRoommateLike.mockReturnValue(previous.promise);
  const first = mount(card());
  fireEvent.click(screen.getByRole("button", { name: "대상20 프로필 좋아요" }));
  first.unmount();
  if (switchSession) await act(async () => { clearStoredAuthentication(); storeAccessToken(jwt(2)); });
  mount(card());
  await act(async () => previous.resolve());
  expect(screen.getByRole("button", { name: "대상20 프로필 좋아요" })).toHaveAttribute("aria-pressed", "false");
});

test("detail refetches for B and ignores A's old detail response", async () => {
  const previous = deferred();
  api.getRoommateDetail.mockReturnValueOnce(previous.promise).mockResolvedValueOnce(target(20, false));
  mount(detail());
  await act(async () => { clearStoredAuthentication(); storeAccessToken(jwt(2)); });
  expect(await screen.findByRole("button", { name: "대상20님 좋아요" })).toHaveAttribute("aria-pressed", "false");
  expect(api.getRoommateDetail).toHaveBeenCalledTimes(2);
  expect(api.getRoommateDetail.mock.calls[0][1].aborted).toBe(true);
  await act(async () => previous.resolve(target(20, true)));
  expect(screen.getByRole("button", { name: "대상20님 좋아요" })).toHaveAttribute("aria-pressed", "false");
});

test("late A success cannot invalidate B's newly loaded MyPage list", async () => {
  const previous = deferred(); api.setRoommateLike.mockReturnValue(previous.promise);
  const view = mount(<>{card()}<MyPage currentUser={account} /></>);
  await screen.findByText("0명");
  fireEvent.click(screen.getByRole("button", { name: "대상20 프로필 좋아요" }));
  await act(async () => { clearStoredAuthentication(); storeAccessToken(jwt(2)); });
  view.rerender(<MemoryRouter initialEntries={["/profile?tab=likes"]}>{card()}<MyPage currentUser={{ ...account, id: 2 }} /></MemoryRouter>);
  await screen.findByText("0명");
  const calls = api.getMyLikedRoommates.mock.calls.length;
  await act(async () => previous.resolve());
  expect(screen.getByText("0명")).toBeInTheDocument();
  expect(api.getMyLikedRoommates).toHaveBeenCalledTimes(calls);
});

test("remounting detail restores server state instead of retaining an old local override", async () => {
  const first = mount(detail());
  fireEvent.click(await screen.findByRole("button", { name: "대상20님 좋아요" }));
  await screen.findByRole("button", { name: "대상20님 좋아요 취소" });
  first.unmount(); mount(detail());
  expect(await screen.findByRole("button", { name: "대상20님 좋아요 취소" })).toHaveAttribute("aria-pressed", "true");
  expect(api.getRoommateDetail).toHaveBeenCalledTimes(2);
});

test("search refetches in the new session, so A's loaded card does not supply B's liked value", async () => {
  ownedLikes().add(20);
  mount(<RoommateSearchPage currentUser={account} />, "/roommates");
  await screen.findByRole("button", { name: "대상20 프로필 좋아요 취소" });
  await act(async () => { clearStoredAuthentication(); storeAccessToken(jwt(2)); });
  expect(await screen.findByRole("button", { name: "대상20 프로필 좋아요" })).toHaveAttribute("aria-pressed", "false");
  expect(api.getRoommates).toHaveBeenCalledTimes(2);
});

test("StrictMode effect cleanup does not disable valid writes or duplicate the MyPage refresh", async () => {
  mount(<StrictMode>{card()}<MyPage currentUser={account} /></StrictMode>);
  await screen.findByText("0명");
  const initialReads = api.getMyLikedRoommates.mock.calls.length;
  fireEvent.click(screen.getByRole("button", { name: "대상20 프로필 좋아요" }));
  await screen.findByText("1명");
  expect(api.setRoommateLike).toHaveBeenCalledTimes(1);
  expect(api.getMyLikedRoommates).toHaveBeenCalledTimes(initialReads + 1);
});
