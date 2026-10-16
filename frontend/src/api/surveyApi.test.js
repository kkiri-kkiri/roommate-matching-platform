import { getMyProfileSurvey, saveMyProfileSurvey } from "./surveyApi";
import { PROFILE_FIELDS } from "../data/profileSurvey";

const mockRequest = jest.fn();
jest.mock("axios", () => ({ create: () => ({
  request: (...args) => mockRequest(...args), interceptors: { response: { use: jest.fn() } },
}) }));

let token;
const completedAnswers = Object.fromEntries(Object.values(PROFILE_FIELDS).map((field) =>
  [field.id, field.type === "multi" ? [field.options[0]] : field.options[0]],
));

beforeEach(() => {
  mockRequest.mockReset();
  window.localStorage.clear();
  token = `test.${window.btoa(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + 3600 }))}.signature`;
  window.localStorage.setItem("accessToken", token);
});

test("GET uses JWT and cancellation signal and retains the unfilled response", async () => {
  const data = { completed: false, answers: null, introduction: "기존 소개" };
  mockRequest.mockResolvedValue({ data: { success: true, data } });
  const signal = new AbortController().signal;
  await expect(getMyProfileSurvey(signal)).resolves.toEqual(data);
  expect(mockRequest).toHaveBeenCalledWith({ method: "get", url: "/users/me/profile-survey", signal,
    headers: { Authorization: `Bearer ${token}` } });
});

test("PUT sends the answers directly and returns persisted answers", async () => {
  const answers = { ...completedAnswers };
  const data = { completed: true, answers, introduction: null };
  mockRequest.mockResolvedValue({ data: { success: true, data } });
  await expect(saveMyProfileSurvey(answers)).resolves.toEqual(data);
  expect(mockRequest).toHaveBeenCalledWith({ method: "put", url: "/users/me/profile-survey", data: answers,
    headers: { Authorization: `Bearer ${token}` } });
});

test("rejects unauthenticated requests and malformed completion responses", async () => {
  window.localStorage.removeItem("accessToken");
  await expect(getMyProfileSurvey()).rejects.toThrow("로그인");
  expect(mockRequest).not.toHaveBeenCalled();
  window.localStorage.setItem("accessToken", token);
  mockRequest.mockResolvedValue({ data: { success: true, data: { completed: true, answers: null } } });
  await expect(getMyProfileSurvey()).rejects.toThrow("응답을 확인");
});

test("propagates server failure without manufacturing successful answers", async () => {
  mockRequest.mockRejectedValue({ response: { data: { message: "저장 실패" } } });
  await expect(saveMyProfileSurvey(completedAnswers)).rejects.toThrow("저장 실패");
  expect(mockRequest).toHaveBeenCalledTimes(1);
});

test("transports introduction with the survey and returns server-normalized text", async () => {
  const answers = { ...completedAnswers, introduction: " \n " };
  const data = { completed: true, answers: completedAnswers, introduction: "" };
  mockRequest.mockResolvedValue({ data: { success: true, data } });
  await expect(saveMyProfileSurvey(answers)).resolves.toEqual(data);
  expect(mockRequest).toHaveBeenCalledWith(expect.objectContaining({ data: answers }));
  mockRequest.mockResolvedValue({ data: { success: true, data: { ...data, introduction: "한글\n소개" } } });
  expect((await getMyProfileSurvey()).introduction).toBe("한글\n소개");
});

test.each(["invalid-token", "expired"])("rejects %s authentication before sending a survey request", async (kind) => {
  const invalidToken = kind === "expired"
    ? `test.${window.btoa(JSON.stringify({ exp: Math.floor(Date.now() / 1000) - 1 }))}.signature`
    : kind;
  window.localStorage.setItem("accessToken", invalidToken);
  await expect(getMyProfileSurvey()).rejects.toThrow("로그인");
  expect(mockRequest).not.toHaveBeenCalled();
  expect(window.localStorage.getItem("accessToken")).toBeNull();
});
