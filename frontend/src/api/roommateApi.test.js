import { getFilteredRecommendedRoommates, getRecommendedRoommates, getRoommateDetail, setRoommateLike } from "./roommateApi";
import { clearStoredAuthentication, getAuthenticationSession, storeAccessToken } from "../auth/session";
const mockRequest = jest.fn();
var mockResponseError;
jest.mock("axios", () => ({ create: () => ({
  request: (config) => mockRequest(config).catch((failure) => mockResponseError({ ...failure, config })),
  interceptors: { response: { use: (success, failure) => { mockResponseError = failure; } } },
}) }));
beforeEach(() => {
  mockRequest.mockReset(); localStorage.clear();
  localStorage.setItem("accessToken", `test.${btoa(JSON.stringify({ exp: Math.floor(Date.now()/1000)+3600 }))}.signature`);
  mockRequest.mockResolvedValue({ data: { data: { content: [], totalPages: 0 } } });
});
test("filtered recommendations send repeated filter values in a single authenticated cancellable request", async () => {
  const signal = new AbortController().signal;
  await getFilteredRecommendedRoommates({ smoking: ["비흡연", "전자담배"], meal: ["같이 먹고 싶음"], sleepTime: [] }, signal);
  expect(mockRequest).toHaveBeenCalledTimes(1);
  const config = mockRequest.mock.calls[0][0];
  expect(config.url).toBe("/roommates/recommended"); expect(config.signal).toBe(signal);
  expect(config.params.getAll("smoking")).toEqual(["비흡연", "전자담배"]);
  expect(config.params.get("meal")).toBe("같이 먹고 싶음");
  expect(config.params.get("page")).toBe("0"); expect(config.params.get("size")).toBe("9");
  expect(config.headers.Authorization).toMatch(/^Bearer /);
});
test("existing recommendation callers retain their signal-only signature", async () => {
  const signal = new AbortController().signal;
  await getRecommendedRoommates(signal);
  expect(mockRequest).toHaveBeenCalledWith(expect.objectContaining({ signal, params: { page: 0, size: 9 } }));
});

test("ProfileLike preserves API method/path and attaches only local session-generation metadata", async () => {
  await setRoommateLike(42, true);
  await setRoommateLike(42, false);
  expect(mockRequest.mock.calls.map(([config]) => [config.method, config.url])).toEqual([
    ["post", "/roommates/42/likes"], ["delete", "/roommates/42/likes"],
  ]);
  expect(mockRequest.mock.calls[0][0].authenticationSessionGeneration).toBe(getAuthenticationSession().generation);
});

test("late 401 from the identical JWT's previous login cannot clear the new session", async () => {
  let reject;
  mockRequest.mockImplementationOnce(() => new Promise((resolve, fail) => { reject = fail; }));
  const token = localStorage.getItem("accessToken");
  const pending = setRoommateLike(42, true);
  clearStoredAuthentication(); storeAccessToken(token);
  const session = getAuthenticationSession();
  reject({ response: { status: 401, data: { message: "이전 요청 실패" } } });
  await expect(pending).rejects.toThrow("이전 요청 실패");
  expect(localStorage.getItem("accessToken")).toBe(token);
  expect(getAuthenticationSession()).toBe(session);
  expect(mockRequest).toHaveBeenCalledTimes(1);
});

test("late optional detail 401 cannot clear B or retry as a guest in B's session", async () => {
  let reject;
  mockRequest.mockImplementationOnce(() => new Promise((resolve, fail) => { reject = fail; }));
  const pending = getRoommateDetail(42);
  storeAccessToken(`other.${btoa(JSON.stringify({ userId: 2, exp: Math.floor(Date.now()/1000)+3600 }))}.signature`);
  const session = getAuthenticationSession();
  reject({ response: { status: 401, data: { message: "이전 상세 실패" } } });
  await expect(pending).rejects.toThrow("이전 상세 실패");
  expect(getAuthenticationSession()).toBe(session);
  expect(mockRequest).toHaveBeenCalledTimes(1);
});

test("a current-session write 401 still clears authentication without retrying the write", async () => {
  mockRequest.mockRejectedValueOnce({ response: { status: 401, data: { message: "로그인이 필요합니다" } } });
  await expect(setRoommateLike(42, false)).rejects.toThrow("로그인이 필요합니다");
  expect(localStorage.getItem("accessToken")).toBeNull();
  expect(mockRequest).toHaveBeenCalledTimes(1);
});
