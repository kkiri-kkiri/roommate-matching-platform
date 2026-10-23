import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import "@testing-library/jest-dom";
import LoginPage from "./LoginPage";
import FindPasswordPage from "./FindPasswordPage";
import SignupPage from "./SignupPage";
import * as api from "../../api/authApi";

const mockNavigate = jest.fn();
jest.mock("react-router-dom", () => ({
  useNavigate: () => mockNavigate,
  Link: ({ to, children, ...props }) => <a href={to} {...props}>{children}</a>,
}));
jest.mock("../../api/authApi");

beforeEach(() => {
  jest.clearAllMocks();
  window.localStorage.clear();
  jest.spyOn(window, "alert").mockImplementation(() => {});
  api.checkSignupAvailability.mockResolvedValue({ schoolEmailAvailable: true, nicknameAvailable: true });
});
afterEach(() => jest.restoreAllMocks());
const timeoutMessage = "응답 시간이 초과되었습니다. 요청이 이미 처리됐을 수 있으니 결과를 확인해 주세요.";

test("login keeps the email-check flow and restores its button after timeout without duplicate submission", async () => {
  api.checkLoginEmail.mockResolvedValue({ registered: true });
  render(<LoginPage />);
  fireEvent.change(screen.getByLabelText("학교 이메일"), { target: { value: "student" } });
  fireEvent.click(screen.getByRole("button", { name: "다음" }));
  fireEvent.change(await screen.findByLabelText("비밀번호"), { target: { value: "Password1!" } });
  let reject;
  api.login.mockImplementation(() => new Promise((_, fail) => { reject = fail; }));
  const button = screen.getByRole("button", { name: "로그인" });
  fireEvent.click(button);
  expect(button).toBeDisabled();
  fireEvent.submit(button.closest("form"));
  expect(api.login).toHaveBeenCalledTimes(1);
  await act(async () => reject(new Error(timeoutMessage)));
  await waitFor(() => expect(button).toBeEnabled());
  expect(window.alert).toHaveBeenCalledWith(timeoutMessage);
  expect(mockNavigate).not.toHaveBeenCalled();
});

test("reset mail timeout restores loading state without automatic resend", async () => {
  let reject;
  api.requestPasswordReset.mockImplementation(() => new Promise((_, fail) => { reject = fail; }));
  render(<FindPasswordPage />);
  fireEvent.change(screen.getByLabelText("학교 이메일"), { target: { value: "student" } });
  const button = screen.getByRole("button", { name: "비밀번호 재설정 이메일 보내기" });
  fireEvent.click(button);
  fireEvent.submit(button.closest("form"));
  expect(button).toBeDisabled();
  expect(api.requestPasswordReset).toHaveBeenCalledTimes(1);
  await act(async () => reject(new Error(timeoutMessage)));
  await waitFor(() => expect(button).toBeEnabled());
  expect(screen.getByText(timeoutMessage)).toBeInTheDocument();
});

test("reset confirmation timeout leaves entered token available and restores the button", async () => {
  api.requestPasswordReset.mockResolvedValue({ sent: true });
  let reject;
  api.confirmPasswordReset.mockImplementation(() => new Promise((_, fail) => { reject = fail; }));
  render(<FindPasswordPage />);
  fireEvent.change(screen.getByLabelText("학교 이메일"), { target: { value: "student" } });
  fireEvent.click(screen.getByRole("button", { name: "비밀번호 재설정 이메일 보내기" }));
  fireEvent.click(await screen.findByRole("button", { name: "메일의 재설정 토큰 입력하기" }));
  fireEvent.change(screen.getByLabelText("재설정 토큰"), { target: { value: "reset-token" } });
  fireEvent.change(screen.getByLabelText("새 비밀번호"), { target: { value: "Password1!" } });
  fireEvent.change(screen.getByLabelText("새 비밀번호 확인"), { target: { value: "Password1!" } });
  const button = screen.getByRole("button", { name: "비밀번호 변경하기" });
  fireEvent.click(button); fireEvent.submit(button.closest("form"));
  expect(api.confirmPasswordReset).toHaveBeenCalledTimes(1);
  await act(async () => reject(new Error(timeoutMessage)));
  await waitFor(() => expect(button).toBeEnabled());
  expect(screen.getByLabelText("재설정 토큰")).toHaveValue("reset-token");
});

test("verification mail timeout restores signup controls and does not resend", async () => {
  let reject;
  api.sendEmailVerification.mockImplementation(() => new Promise((_, fail) => { reject = fail; }));
  render(<SignupPage />);
  fireEvent.change(screen.getByLabelText("학교 이메일"), { target: { value: "student" } });
  const button = screen.getByRole("button", { name: "인증번호 전송" });
  fireEvent.click(button); fireEvent.click(button);
  expect(api.sendEmailVerification).toHaveBeenCalledTimes(1);
  expect(screen.getByRole("button", { name: "회원가입" })).toBeDisabled();
  await act(async () => reject(new Error(timeoutMessage)));
  await waitFor(() => expect(button).toBeEnabled());
  expect(screen.getByRole("button", { name: "회원가입" })).toBeEnabled();
  expect(window.alert).toHaveBeenCalledWith(timeoutMessage);
});

test("expired signup verification unlocks re-verification while preserving the completed signup draft", async () => {
  api.sendEmailVerification.mockResolvedValue(undefined);
  api.verifyEmailVerification.mockResolvedValue("synthetic-verification");
  api.signup.mockRejectedValueOnce(Object.assign(new Error("다시 인증해 주세요."), {
    errorCode: "EMAIL_VERIFICATION_TOKEN_INVALID",
  })).mockResolvedValueOnce({ id: 1 });
  const { container } = render(<SignupPage />);
  const change = (id, value) => fireEvent.change(container.querySelector(`#${id}`), { target: { value } });
  change("signup-school-email", "student");
  change("signup-password", "Password1!"); change("signup-password-confirm", "Password1!");
  change("signup-nickname", "학생");
  fireEvent.click(screen.getByRole("button", { name: "중복 확인" }));
  await waitFor(() => expect(api.checkSignupAvailability).toHaveBeenCalled());
  for (const name of ["gender", "dormitory", "grade"]) {
    fireEvent.click(container.querySelector(`label[for="signup-${name}-0"]`));
  }
  change("signup-faculty", "HK자율전공학부");
  change("signup-birth-year", "2003"); change("signup-student-id", "22");
  for (const name of ["에너지 방향 I", "인식 방식 N", "판단 방식 T", "생활 양식 J"]) {
    fireEvent.click(screen.getByRole("button", { name, exact: true }));
  }
  async function verify() {
    fireEvent.click(screen.getByRole("button", { name: "인증번호 전송", exact: true }));
    await waitFor(() => expect(screen.getByRole("button", { name: "인증 확인" })).toBeEnabled());
    change("signup-verification-code", "123456");
    fireEvent.click(screen.getByRole("button", { name: "인증 확인" }));
    await waitFor(() => expect(screen.getByRole("button", { name: "인증 완료" })).toBeDisabled());
  }
  await verify();
  fireEvent.click(screen.getByRole("button", { name: "회원가입", exact: true }));
  await waitFor(() => expect(screen.getByRole("button", { name: "인증번호 전송", exact: true })).toBeEnabled());
  expect(api.signup).toHaveBeenCalledTimes(1);
  expect(container.querySelector("#signup-school-email").readOnly).toBe(false);
  expect(container.querySelector("#signup-nickname")).toHaveValue("학생");
  expect(container.querySelector("#signup-password")).toHaveValue("Password1!");
  expect(container.querySelector("#signup-faculty")).toHaveValue("HK자율전공학부");
  expect(mockNavigate).not.toHaveBeenCalled();
  await verify();
  fireEvent.click(screen.getByRole("button", { name: "회원가입", exact: true }));
  await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith("/login"));
  expect(api.signup).toHaveBeenCalledTimes(2);
}, 20_000);
