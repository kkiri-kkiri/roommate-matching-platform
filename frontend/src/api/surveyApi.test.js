import { getMyProfileSurvey, saveMyProfileSurvey } from "./surveyApi";

const mockRequest = jest.fn();
jest.mock("axios", () => ({ create: () => ({ request: (...args) => mockRequest(...args) }) }));

beforeEach(() => {
  mockRequest.mockReset();
  window.localStorage.clear();
  window.localStorage.setItem("accessToken", "test-token");
});

test("GET uses JWT and cancellation signal and retains the unfilled response", async () => {
  const data = { completed: false, answers: null };
  mockRequest.mockResolvedValue({ data: { success: true, data } });
  const signal = new AbortController().signal;
  await expect(getMyProfileSurvey(signal)).resolves.toEqual(data);
  expect(mockRequest).toHaveBeenCalledWith({ method: "get", url: "/users/me/profile-survey", signal,
    headers: { Authorization: "Bearer test-token" } });
});

test("PUT sends the answers directly and returns persisted answers", async () => {
  const answers = { sleepTime: "22시 이전", wakeTime: "07시 이전", lightingPreference: "상관없음", meal: "같이 먹고 싶음" };
  const data = { completed: true, answers };
  mockRequest.mockResolvedValue({ data: { success: true, data } });
  await expect(saveMyProfileSurvey(answers)).resolves.toEqual(data);
  expect(mockRequest).toHaveBeenCalledWith({ method: "put", url: "/users/me/profile-survey", data: answers,
    headers: { Authorization: "Bearer test-token" } });
});

test("rejects unauthenticated requests and malformed completion responses", async () => {
  window.localStorage.removeItem("accessToken");
  await expect(getMyProfileSurvey()).rejects.toThrow("로그인");
  expect(mockRequest).not.toHaveBeenCalled();
  window.localStorage.setItem("accessToken", "test-token");
  mockRequest.mockResolvedValue({ data: { data: { completed: true, answers: null } } });
  await expect(getMyProfileSurvey()).rejects.toThrow("응답을 확인");
});

test("propagates server failure without manufacturing successful answers", async () => {
  mockRequest.mockRejectedValue({ response: { data: { message: "저장 실패" } } });
  await expect(saveMyProfileSurvey({})).rejects.toThrow("저장 실패");
});
