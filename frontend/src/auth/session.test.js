import { act, renderHook, waitFor } from "@testing-library/react";
import { getAuthenticatedUser } from "../api/authApi";
import { useAuthentication } from "./useAuthentication";
import { getAuthenticationSession, isCurrentAuthenticationSession, storeAccessToken,
  useAuthenticationSession } from "./session";

jest.mock("../api/authApi");
const token = () => `test.${btoa(JSON.stringify({ userId: 1, exp: Math.floor(Date.now()/1000)+3600 }))}.signature`;

beforeEach(() => {
  jest.clearAllMocks(); localStorage.clear(); storeAccessToken(token());
  getAuthenticatedUser.mockResolvedValue({ id: 1, nickname: "사용자", role: "USER" });
});

test("verified focus refresh and user metadata updates preserve the active generation", async () => {
  const session = getAuthenticationSession();
  const { result } = renderHook(() => useAuthentication());
  await waitFor(() => expect(result.current.currentUser?.id).toBe(1));
  await act(async () => window.dispatchEvent(new Event("focus")));
  expect(getAuthenticatedUser).toHaveBeenCalledTimes(2);
  act(() => result.current.handleLogin({ id: 1, nickname: "새 닉네임", role: "USER" }));
  expect(getAuthenticationSession()).toBe(session);
});

test("login creates a new generation even when the user and JWT are identical", () => {
  const currentToken = localStorage.getItem("accessToken");
  const previous = getAuthenticationSession();
  storeAccessToken(currentToken);
  expect(getAuthenticationSession().userId).toBe(previous.userId);
  expect(getAuthenticationSession().generation).toBe(previous.generation + 1);
  expect(isCurrentAuthenticationSession(previous)).toBe(false);
});

test("cross-tab logout/relogin advances once per event for all subscribers, even with the same final token", () => {
  const { result } = renderHook(() => [useAuthenticationSession(), useAuthenticationSession()]);
  const previous = result.current[0];
  act(() => window.dispatchEvent(new StorageEvent("storage", { key: "accessToken", newValue: null })));
  expect(result.current[0].generation).toBe(previous.generation + 1);
  act(() => window.dispatchEvent(new StorageEvent("storage", { key: "accessToken", newValue: localStorage.getItem("accessToken") })));
  expect(result.current[0].generation).toBe(previous.generation + 2);
  expect(result.current[1]).toBe(result.current[0]);
  expect(isCurrentAuthenticationSession(previous)).toBe(false);
});
