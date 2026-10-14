import axios from "axios";
import { login, sendEmailVerification, verifyEmailVerification, signup, requestPasswordReset, confirmPasswordReset } from "./authApi";

jest.mock("axios", () => ({ create: jest.fn(() => ({
  get: jest.fn(), post: jest.fn(), interceptors: { response: { use: jest.fn() } },
})) }));
const client = axios.create.mock.results[1].value;
const clientOptions = axios.create.mock.calls[1][0];

beforeEach(() => { client.post.mockReset(); client.get.mockReset(); });

test("uses finite timeouts and preserves auth payloads and envelopes", async () => {
  expect(clientOptions.timeout).toBe(15000);
  client.post.mockResolvedValue({ data: { data: { accessToken: "jwt", user: { id: 1 } } } });
  await expect(login("student@hknu.ac.kr", "Password1!")).resolves.toEqual({ accessToken: "jwt", user: { id: 1 } });
  expect(client.post).toHaveBeenLastCalledWith("/auth/login", { email: "student@hknu.ac.kr", password: "Password1!" });
  await sendEmailVerification("student@hknu.ac.kr");
  expect(client.post).toHaveBeenLastCalledWith("/auth/email-verifications/send", { email: "student@hknu.ac.kr", purpose: "SIGNUP" }, { timeout: 30000 });
  await requestPasswordReset("student@hknu.ac.kr");
  expect(client.post).toHaveBeenLastCalledWith("/auth/password-reset/request", { email: "student@hknu.ac.kr" }, { timeout: 30000 });
  client.post.mockResolvedValue({ data: { data: { verificationToken: "verified" } } });
  await expect(verifyEmailVerification("student@hknu.ac.kr", "123456")).resolves.toBe("verified");
  expect(client.post).toHaveBeenLastCalledWith("/auth/email-verifications/verify", { email: "student@hknu.ac.kr", code: "123456", purpose: "SIGNUP" });
  await confirmPasswordReset("reset", "Password1!", "Password1!");
  expect(client.post).toHaveBeenLastCalledWith("/auth/password-reset/confirm", { resetToken: "reset", newPassword: "Password1!", newPasswordConfirm: "Password1!" });
});

test.each([
  ["login", () => login("student@hknu.ac.kr", "Password1!")],
  ["signup", () => signup({ nickname: "학생" })],
  ["reset", () => confirmPasswordReset("reset", "Password1!", "Password1!")],
  ["mail", () => requestPasswordReset("student@hknu.ac.kr")],
])("%s timeout warns of ambiguous completion and never retries", async (_, request) => {
  client.post.mockRejectedValue({ code: "ECONNABORTED" });
  await expect(request()).rejects.toThrow("이미 처리됐을 수");
  expect(client.post).toHaveBeenCalledTimes(1);
});

test("network and limit errors receive actionable messages", async () => {
  client.post.mockRejectedValue({ code: "ERR_NETWORK" });
  await expect(login("student@hknu.ac.kr", "Password1!")).rejects.toThrow("연결 상태");
  client.post.mockRejectedValue({ response: { status: 429, data: { message: "요청이 너무 많습니다." } } });
  await expect(login("student@hknu.ac.kr", "Password1!")).rejects.toThrow("요청이 너무 많습니다.");
});
