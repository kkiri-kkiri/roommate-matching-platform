import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import "@testing-library/jest-dom";
import RoommateSearchPage from "./RoommateSearchPage";
import * as api from "../../api/roommateApi";

let mockInitialTab = "recommended";
jest.mock("react-router-dom", () => ({
  useSearchParams: () => require("react").useState(() => new URLSearchParams({ tab: mockInitialTab })),
  Link: ({ to, children, ...props }) => <a href={to} {...props}>{children}</a>,
}));
jest.mock("../../api/roommateApi");
jest.mock("../../components/common/ProfileCard/ProfileCard", () => ({ profile }) => <article>{profile.nickname}</article>);

const page = (nickname, totalPages = 1) => ({ content: nickname ? [{ id: 42, nickname }] : [], totalPages });
beforeEach(() => {
  jest.resetAllMocks(); mockInitialTab = "recommended";
  HTMLDialogElement.prototype.showModal = jest.fn(function () { this.open = true; });
  HTMLDialogElement.prototype.close = jest.fn(function () { this.open = false; });
  api.toRoommateProfile.mockImplementation(item => item);
  api.getFilteredRecommendedRoommates.mockResolvedValue(page("추천 사용자"));
  api.getRoommates.mockResolvedValue(page("전체 사용자", 10000));
});

function applySleepFilter() {
  fireEvent.click(screen.getByRole("button", { name: "필터 열기" }));
  fireEvent.click(screen.getByRole("checkbox", { name: "22시 이전" }));
  fireEvent.click(screen.getByRole("button", { name: "적용" }));
}

test("recommended filters use one server request regardless of the total population", async () => {
  render(<RoommateSearchPage currentUser={{ id: 1 }} />);
  expect(await screen.findByText("추천 사용자")).toBeInTheDocument();
  api.getFilteredRecommendedRoommates.mockResolvedValue(page("서버 필터 결과", 10000));
  applySleepFilter();
  expect(await screen.findByText("서버 필터 결과")).toBeInTheDocument();
  expect(api.getFilteredRecommendedRoommates).toHaveBeenCalledTimes(2);
  expect(api.getFilteredRecommendedRoommates.mock.calls[1][0].sleepTime).toEqual(["22시 이전"]);
  expect(api.getRoommates).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole("button", { name: "필터 초기화" }));
  await waitFor(() => expect(api.getFilteredRecommendedRoommates).toHaveBeenCalledTimes(3));
  expect(api.getFilteredRecommendedRoommates.mock.calls[2][0].sleepTime).toEqual([]);
});

test("rapid filter changes cancel prior requests and ignore late responses", async () => {
  let resolveOld, resolveLatest;
  api.getFilteredRecommendedRoommates.mockResolvedValueOnce(page("초기 추천"))
    .mockImplementationOnce(() => new Promise(resolve => { resolveOld = resolve; }))
    .mockImplementationOnce(() => new Promise(resolve => { resolveLatest = resolve; }));
  const view = render(<RoommateSearchPage currentUser={{ id: 1 }} />);
  await screen.findByText("초기 추천"); applySleepFilter();
  await waitFor(() => expect(api.getFilteredRecommendedRoommates).toHaveBeenCalledTimes(2));
  const oldSignal = api.getFilteredRecommendedRoommates.mock.calls[1][1];
  fireEvent.click(screen.getByRole("button", { name: "취침시간 22시 이전 필터 취소" }));
  await waitFor(() => expect(api.getFilteredRecommendedRoommates).toHaveBeenCalledTimes(3));
  expect(oldSignal.aborted).toBe(true);
  await act(async () => resolveLatest(page("최신 추천")));
  await act(async () => resolveOld(page("오래된 추천")));
  expect(screen.getByText("최신 추천")).toBeInTheDocument();
  expect(screen.queryByText("오래된 추천")).not.toBeInTheDocument();
  view.unmount(); expect(api.getFilteredRecommendedRoommates.mock.calls[2][1].aborted).toBe(true);
  expect(api.getRoommates).not.toHaveBeenCalled();
});

test("empty recommendations stay empty without fetching all profiles", async () => {
  api.getFilteredRecommendedRoommates.mockResolvedValue(page(null, 0));
  render(<RoommateSearchPage currentUser={{ id: 1 }} />);
  expect(await screen.findByText("조건에 맞는 추천 프로필이 없어요")).toBeInTheDocument();
  applySleepFilter();
  await waitFor(() => expect(api.getFilteredRecommendedRoommates).toHaveBeenCalledTimes(2));
  expect(api.getRoommates).not.toHaveBeenCalled();
});

test("all profiles keep page size nine and guests cannot request recommendations", async () => {
  mockInitialTab = "all";
  const view = render(<RoommateSearchPage />);
  await screen.findByText("전체 사용자");
  expect(api.getRoommates.mock.calls[0][0]).toMatchObject({ page: 0, size: 9 });
  fireEvent.click(screen.getByRole("tab", { name: "추천 프로필" }));
  expect(await screen.findByText("로그인 후 추천 프로필을 확인할 수 있어요.")).toBeInTheDocument();
  expect(api.getFilteredRecommendedRoommates).not.toHaveBeenCalled(); view.unmount();
});
