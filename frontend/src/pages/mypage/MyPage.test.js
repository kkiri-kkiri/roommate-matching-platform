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
  expect(surveyApi.saveMyProfileSurvey).toHaveBeenCalledWith({ ...answers, introduction: "" });
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

test("introduction is last and optional, saves with all answers, and reloads the server result", async () => {
  surveyApi.getMyProfileSurvey.mockResolvedValue({ completed: true, answers, introduction: "기존\n소개" });
  surveyApi.saveMyProfileSurvey.mockResolvedValue({ completed: true, answers, introduction: "서버\n응답" });
  const view = render(<MyPage currentUser={user} />);
  fireEvent.click(await screen.findByRole("button", { name: "설문 수정" }));
  const input = screen.getByLabelText("나의 한마디 (선택)");
  expect(input).toHaveValue("기존\n소개");
  expect(input).toHaveAttribute("maxlength", "200");
  expect(screen.getAllByRole("heading", { level: 3 }).at(-1)).toHaveTextContent("나의 한마디");
  fireEvent.change(input, { target: { value: "새로운\n소개" } });
  expect(screen.getByText("6 / 200")).toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "저장" }));
  await waitFor(() => expect(surveyApi.saveMyProfileSurvey).toHaveBeenCalledWith({ ...answers, introduction: "새로운\n소개" }));
  await waitFor(() => expect(screen.getByText(/서버\s+응답/)).toBeInTheDocument());
  view.unmount();
  surveyApi.getMyProfileSurvey.mockResolvedValue({ completed: true, answers, introduction: "서버\n응답" });
  render(<MyPage currentUser={user} />);
  fireEvent.click(await screen.findByRole("button", { name: "설문 수정" }));
  expect(screen.getByLabelText("나의 한마디 (선택)")).toHaveValue("서버\n응답");
});

test("enforces UTF-16 limit and retains the draft after save failure", async () => {
  surveyApi.getMyProfileSurvey.mockResolvedValue({ completed: true, answers, introduction: "" });
  surveyApi.saveMyProfileSurvey.mockRejectedValue(new Error("저장 실패"));
  render(<MyPage currentUser={user} />);
  fireEvent.click(await screen.findByRole("button", { name: "설문 수정" }));
  const input = screen.getByLabelText("나의 한마디 (선택)");
  fireEvent.change(input, { target: { value: "😀".repeat(100) } });
  expect(screen.getByText("200 / 200")).toBeInTheDocument();
  fireEvent.change(input, { target: { value: "가".repeat(201) } });
  expect(input).toHaveValue("😀".repeat(100));
  fireEvent.click(screen.getByRole("button", { name: "저장" }));
  await waitFor(() => expect(window.alert).toHaveBeenCalledWith("저장 실패"));
  expect(input).toHaveValue("😀".repeat(100));
});

test("clears introduction and displays the empty fallback without losing survey completion", async () => {
  surveyApi.getMyProfileSurvey.mockResolvedValue({ completed: true, answers, introduction: "기존 소개" });
  surveyApi.saveMyProfileSurvey.mockResolvedValue({ completed: true, answers, introduction: "" });
  render(<MyPage currentUser={user} />);
  fireEvent.click(await screen.findByRole("button", { name: "설문 수정" }));
  fireEvent.change(screen.getByLabelText("나의 한마디 (선택)"), { target: { value: "" } });
  fireEvent.click(screen.getByRole("button", { name: "저장" }));
  expect(await screen.findByText("아직 한마디를 작성하지 않았어요.")).toBeInTheDocument();
  expect(surveyApi.saveMyProfileSurvey).toHaveBeenCalledWith({ ...answers, introduction: "" });
  expect(screen.getByRole("button", { name: "설문 수정" })).toBeInTheDocument();
});

test("preserves legacy text on load and allows shortening it to the new limit", async () => {
  surveyApi.getMyProfileSurvey.mockResolvedValue({ completed: true, answers, introduction: "가".repeat(255) });
  render(<MyPage currentUser={user} />);
  fireEvent.click(await screen.findByRole("button", { name: "설문 수정" }));
  const input = screen.getByLabelText("나의 한마디 (선택)");
  expect(input).toHaveValue("가".repeat(255));
  fireEvent.change(input, { target: { value: "가".repeat(254) } });
  expect(input).toHaveValue("가".repeat(254));
  fireEvent.click(screen.getByRole("button", { name: "저장" }));
  expect(surveyApi.saveMyProfileSurvey).not.toHaveBeenCalled();
  expect(screen.getByRole("alert")).toHaveTextContent("200자 이하");
  fireEvent.change(input, { target: { value: "가".repeat(200) } });
  expect(input).toHaveValue("가".repeat(200));
});
