import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import "@testing-library/jest-dom";
import MyPage from "./MyPage";
import { PROFILE_FIELDS } from "../../data/profileSurvey";
import * as surveyApi from "../../api/surveyApi";
import * as userApi from "../../api/userApi";

let mockTab = "survey";
jest.mock("react-router-dom", () => ({
  useSearchParams: () => [new URLSearchParams({ tab: mockTab })],
  Link: ({ to, children, ...props }) => <a href={to} {...props}>{children}</a>,
}));
jest.mock("../../api/surveyApi");
jest.mock("../../api/userApi");
jest.mock("../../components/common/ProfileCard/ProfileCard", () => () => null);

const user = { id: 1, nickname: "서버 사용자", major: "소프트웨어융합전공", dormitory: "나래", admissionYear: "24" };
const answers = Object.fromEntries(Object.values(PROFILE_FIELDS).map(field =>
  [field.id, field.type === "multi" ? [field.options[0]] : field.options[0]],
));
beforeEach(() => {
  jest.resetAllMocks();
  mockTab = "survey";
  window.scrollTo = jest.fn();
  window.alert = jest.fn();
  window.localStorage.setItem("roomieProfileSurveys", JSON.stringify([{ id: "survey:server", answers, profile: user }]));
  userApi.getMyInfo.mockResolvedValue(user);
  surveyApi.getMyPreferences.mockResolvedValue({ completed: false, answers: null });
  surveyApi.getMyProfileSurvey.mockResolvedValue({ completed: false, answers: null });
});

test("ignores local storage and keeps a new or unsaved survey incomplete", async () => {
  render(<MyPage currentUser={user} />);
  fireEvent.click(await screen.findByRole("button", { name: "내 설문 작성하기" }));
  expect(screen.getByLabelText("취침시간")).toHaveValue("");
  fireEvent.change(screen.getByLabelText("취침시간"), { target: { value: "22시 이전" } });
  fireEvent.click(screen.getByRole("button", { name: "저장" }));
  expect(surveyApi.saveMyProfileSurvey).not.toHaveBeenCalled();
  expect(screen.getByText("룸메이트와의 식사 방식 항목을 선택해 주세요.")).toBeInTheDocument();
});

test("loads DB values and displays the save response, including meal and multiple answers", async () => {
  surveyApi.getMyProfileSurvey.mockResolvedValue({ completed: true, answers });
  const saved = { ...answers, meal: "각자 먹고 싶음" };
  surveyApi.saveMyProfileSurvey.mockResolvedValue({ completed: true, answers: saved });
  const view = render(<MyPage currentUser={user} />);
  fireEvent.click(await screen.findByRole("button", { name: "설문 수정" }));
  expect(screen.getByLabelText("취침시간")).toHaveValue("22시 이전");
  expect(screen.getByLabelText("기상시간")).toHaveValue("07시 이전");
  fireEvent.click(screen.getByRole("button", { name: "저장" }));
  await waitFor(() => expect(screen.getByText("각자 먹고 싶음")).toBeInTheDocument());
  expect(surveyApi.saveMyProfileSurvey).toHaveBeenCalledWith(answers);
  view.unmount();
  surveyApi.getMyProfileSurvey.mockResolvedValue({ completed: true, answers: saved });
  render(<MyPage currentUser={user} />);
  expect(await screen.findByText("각자 먹고 싶음")).toBeInTheDocument();
});

test("failed save preserves draft and does not report completion", async () => {
  surveyApi.getMyProfileSurvey.mockResolvedValue({ completed: false, answers });
  surveyApi.saveMyProfileSurvey.mockRejectedValue(new Error("저장 실패"));
  render(<MyPage currentUser={user} />);
  fireEvent.click(await screen.findByRole("button", { name: "내 설문 작성하기" }));
  fireEvent.click(screen.getByRole("button", { name: "저장" }));
  await waitFor(() => expect(window.alert).toHaveBeenCalledWith("저장 실패"));
  expect(screen.getByLabelText("취침시간")).toHaveValue("22시 이전");
  expect(screen.queryByRole("button", { name: "설문 수정" })).not.toBeInTheDocument();
});

test("lookup failure is distinct from unfilled and supports retry", async () => {
  surveyApi.getMyProfileSurvey.mockRejectedValueOnce(new Error("조회 실패"));
  render(<MyPage currentUser={user} />);
  expect(await screen.findByRole("alert")).toHaveTextContent("조회 실패");
  expect(screen.queryByRole("button", { name: "내 설문 작성하기" })).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));
  expect(await screen.findByRole("button", { name: "내 설문 작성하기" })).toBeInTheDocument();
});

test("completion summary remains unknown while fetching and follows backend completed", async () => {
  mockTab = "profile";
  let resolve;
  surveyApi.getMyProfileSurvey.mockReturnValue(new Promise(done => { resolve = done; }));
  render(<MyPage currentUser={user} />);
  expect(await screen.findByText("설문 확인 중")).toBeInTheDocument();
  expect(screen.queryByText("작성 완료")).not.toBeInTheDocument();
  resolve({ completed: false, answers });
  expect(await screen.findByText("0 / 2 완료")).toBeInTheDocument();
});
