import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import "@testing-library/jest-dom";
import MyPage from "./MyPage";
import * as userApi from "../../api/userApi";
import * as surveyApi from "../../api/surveyApi";
import { storeAccessToken } from "../../auth/session";

let mockTab = "account";
jest.mock("react-router-dom", () => ({
  useSearchParams: () => [new URLSearchParams({ tab: mockTab })],
  Link: ({ to, children, ...props }) => <a href={to} {...props}>{children}</a>,
}));
jest.mock("../../api/userApi");
jest.mock("../../api/surveyApi");
jest.mock("../../components/common/ProfileCard/ProfileCard", () => () => null);
const account = { id: 1, nickname: "계정하나", faculty: "HK자율전공학부", major: null, dormitory: "나래", grade: "1학년", admissionYear: "24" };
const token = id => "test." + window.btoa(JSON.stringify({ userId: id, exp: 9999999999 })) + ".test";
beforeEach(() => {
  jest.resetAllMocks(); window.scrollTo = jest.fn(); window.alert = jest.fn(); mockTab = "account";
  storeAccessToken(token(1));
  userApi.getMyInfo.mockResolvedValue(account);
  surveyApi.getMyPreferences.mockResolvedValue({ completed: false, answers: null });
  surveyApi.getMyProfileSurvey.mockResolvedValue({ completed: false, answers: null });
});

test("isolated preference lookup failure leaves account visible and has its own retry", async () => {
  surveyApi.getMyPreferences.mockRejectedValueOnce(new Error("선호 조회 실패"));
  const view = render(<MyPage currentUser={account} />);
  expect(await screen.findByRole("heading", { name: "계정 정보", exact: true })).toBeVisible();
  mockTab = "preferences"; view.rerender(<MyPage currentUser={account} />);
  expect(await screen.findByRole("alert")).toHaveTextContent("선호 조회 실패");
  fireEvent.click(screen.getByRole("button", { name: "다시 시도", exact: true }));
  expect(await screen.findByRole("button", { name: "선호 룸메 설문 작성하기", exact: true })).toBeVisible();
  expect(userApi.getMyInfo).toHaveBeenCalledTimes(1);
  expect(surveyApi.getMyPreferences).toHaveBeenCalledTimes(2);
});

test("a previous account's committed but delayed save cannot call the app login updater after account switching", async () => {
  let resolveSave;
  userApi.updateMyInfo.mockImplementation(() => new Promise(resolve => { resolveSave = resolve; }));
  const updated = jest.fn(), view = render(<MyPage currentUser={account} onUserUpdated={updated} />);
  const section = (await screen.findByRole("heading", { name: "기본 정보", exact: true })).closest("section");
  fireEvent.click(within(section).getByRole("button", { name: "수정", exact: true }));
  fireEvent.change(screen.getByLabelText(/닉네임/), { target: { value: "늦은저장" } });
  fireEvent.click(within(section).getByRole("button", { name: "완료", exact: true }));
  await waitFor(() => expect(userApi.updateMyInfo).toHaveBeenCalledTimes(1));
  const next = { ...account, id: 2, nickname: "계정둘" };
  userApi.getMyInfo.mockResolvedValue(next);
  act(() => storeAccessToken(token(2)));
  view.rerender(<MyPage currentUser={next} onUserUpdated={updated} />);
  await screen.findAllByText("계정둘");
  await act(async () => resolveSave({ ...account, nickname: "늦은저장" }));
  expect(updated).not.toHaveBeenCalled();
  expect(screen.queryByText("늦은저장")).not.toBeInTheDocument();
});
