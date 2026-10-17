import { getFilteredRecommendedRoommates, getRecommendedRoommates } from "./roommateApi";
const mockRequest = jest.fn();
jest.mock("axios", () => ({ create: () => ({
  request: (...args) => mockRequest(...args), interceptors: { response: { use: jest.fn() } },
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
